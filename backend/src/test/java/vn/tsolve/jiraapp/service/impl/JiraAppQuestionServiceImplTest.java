package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.LocalTenantScope;
import vn.tsolve.jiraapp.dto.JiraAppQuestionJobResult;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAppRatingRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility.Status;
import vn.tsolve.jiraapp.exception.JiraAppQuestionJobNotFoundException;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.exception.JiraSiteNotConnectedException;
import vn.tsolve.jiraapp.service.JiraAskerLookup;
import vn.tsolve.jiraapp.service.JiraWorkspaceLookup;
import vn.tsolve.project.service.ProjectService;

class JiraAppQuestionServiceImplTest {

    // Any Jira user, linked to T-Solve or not: Ask T-Solve needs no sign-in (KAN-294).
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud-1", "jira-1", null, "DP");
    private static final UUID PROJECT = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final ChatbotAnswerResponse ANSWER = new ChatbotAnswerResponse(
            UUID.fromString("70000000-0000-0000-0000-000000000001"),
            Outcome.ANSWERED,
            "Restart the VPN [1]",
            List.of());

    private static final UUID WORKSPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private final JiraWorkspaceLookup workspaces = mock(JiraWorkspaceLookup.class);
    private final LocalTenantScope tenantScope = new LocalTenantScope();
    private final ProjectService projects = mock(ProjectService.class);
    private final JiraAskerLookup askers = mock(JiraAskerLookup.class);
    private final ChatbotAnswerService answers = mock(ChatbotAnswerService.class);
    private final Queue<Runnable> queued = new ArrayDeque<>();
    private boolean rejectTasks;
    private final Executor executor = task -> {
        if (rejectTasks) {
            throw new RejectedExecutionException("private executor error");
        }
        queued.add(task);
    };
    private final InMemoryAskJobStore jobs = new InMemoryAskJobStore();
    private final AtomicReference<CompletableFuture<ChatbotAnswerResponse>> pending = new AtomicReference<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(JiraAppQuestionServiceImpl.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void registerTheSiteAndProject() {
        appender.start();
        logger.addAppender(appender);
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.of(WORKSPACE));
        when(projects.findActivePublicIdByJiraProjectKey("DP")).thenReturn(Optional.of(PROJECT));
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void answers503Msg06WhenRedisCannotCreateTheJob_AC_010_4_AC_010_18() {
        jobs.failCreates = true;

        assertThatThrownBy(() -> service(Duration.ofSeconds(60))
                        .start(CALLER, new JiraAppQuestionRequest("private question", List.of())))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("MSG06");
                });
        verifyNoInteractions(answers);
        assertThat(queued).isEmpty();
        assertThat(jobs.records).isEmpty();
        assertOnlyLog(jobs.lastCreateId, "RedisConnectionFailureException");
    }

    @Test
    void answers503Msg06WhenRedisCannotReadTheJob_AC_010_4_AC_010_18() {
        UUID id = UUID.randomUUID();
        jobs.failReads = true;

        assertThatThrownBy(() -> service(Duration.ofSeconds(60)).result(CALLER, id))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("MSG06");
                });
        assertOnlyLog(id, "RedisConnectionFailureException");
    }

    @Test
    void schedulesTheCompletionOnTheAskPool_AC_010_4_AC_010_17() {
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);
        var service = service(Duration.ofSeconds(60));
        var job = service.start(CALLER, new JiraAppQuestionRequest("q", List.of()));

        queued.remove().run();

        assertThat(pending.get()).isCompletedWithValue(ANSWER);
        assertThat(service.result(CALLER, job.jobId()).status()).isEqualTo(JiraAppQuestionJobResult.Status.PENDING);
        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(service.result(CALLER, job.jobId()))
                .isEqualTo(new JiraAppQuestionJobResult(JiraAppQuestionJobResult.Status.DONE, ANSWER));
    }

    @Test
    void schedulesTimeoutUpdatesOffTheCompletingThread_AC_010_4_AC_010_17() {
        var service = service(Duration.ofSeconds(60));
        var job = service.start(CALLER, new JiraAppQuestionRequest("q", List.of()));

        pending.get().completeExceptionally(new TimeoutException("private timeout"));

        assertThat(service.result(CALLER, job.jobId()).status()).isEqualTo(JiraAppQuestionJobResult.Status.PENDING);
        assertThat(queued).hasSize(2);
        queued.remove().run();
        verifyNoInteractions(answers);
        queued.remove().run();
        assertThat(service.result(CALLER, job.jobId()).status()).isEqualTo(JiraAppQuestionJobResult.Status.FAILED);
        assertOnlyLog(job.jobId(), "TimeoutException");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completesInlineWhenTheAskPoolRejectsTheCallback_AC_010_4_AC_010_17(boolean timeout) {
        var service = service(Duration.ofSeconds(60));
        var job = service.start(CALLER, new JiraAppQuestionRequest("q", List.of()));
        rejectTasks = true;

        if (timeout) {
            pending.get().completeExceptionally(new TimeoutException("private timeout"));
        } else {
            pending.get().complete(ANSWER);
        }

        assertThat(service.result(CALLER, job.jobId()))
                .isEqualTo(new JiraAppQuestionJobResult(
                        timeout ? JiraAppQuestionJobResult.Status.FAILED : JiraAppQuestionJobResult.Status.DONE,
                        timeout ? null : ANSWER));
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains("Jira App question job " + job.jobId() + ": error type RejectedExecutionException");
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("private", ANSWER.text());
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void logsTheChatbotFailureInsteadOfCompletionException_AC_010_12_AC_010_17() {
        when(answers.askForProject(any(), any(), any())).thenThrow(new AiUnavailableException());
        var job = service(Duration.ofSeconds(60)).start(CALLER, new JiraAppQuestionRequest("q", List.of()));

        runQueuedTasks();

        assertOnlyLog(job.jobId(), "AiUnavailableException");
    }

    @Test
    void unwrapsNestedCompletionAndExecutionExceptionsForLogging_AC_010_12_AC_010_17() {
        var job = service(Duration.ofSeconds(60)).start(CALLER, new JiraAppQuestionRequest("q", List.of()));

        pending.get()
                .completeExceptionally(new CompletionException(
                        new ExecutionException(new CompletionException(new TimeoutException("private timeout")))));
        runQueuedTasks();

        assertOnlyLog(job.jobId(), "TimeoutException");
    }

    private void assertOnlyLog(UUID id, String errorType) {
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                    .isEqualTo("Jira App question job " + id + ": error type " + errorType);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private void runQueuedTasks() {
        while (!queued.isEmpty()) {
            queued.remove().run();
        }
    }

    @Test
    void asksOnItsOwnPoolNotTheSharedTaskExecutor_BUG_183() {
        Constructor<?> springConstructor = Arrays.stream(JiraAppQuestionServiceImpl.class.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .findFirst()
                .orElseThrow();

        assertThat(springConstructor.getParameterTypes()).doesNotContain(Executor.class);
    }

    @Test
    void buildsABoundedPoolThatRefusesWhenFull_BUG_183() {
        ThreadPoolExecutor pool = JiraAppQuestionServiceImpl.askPool(3);
        try {
            assertThat(pool.getCorePoolSize()).isEqualTo(3);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(3);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(3);
            assertThat(pool.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void refusesAFullPoolAndDeletesTheJob_AC_010_18_BUG_183() {
        Executor full = task -> {
            throw new RejectedExecutionException();
        };
        var service = new JiraAppQuestionServiceImpl(
                workspaces, tenantScope, projects, askers, answers, jobs, full, Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.start(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("MSG06");
                });
        assertThat(jobs.records).isEmpty();
        assertThat(jobs.created).isEqualTo(1);
        verifyNoInteractions(answers);
        assertThat(jobs.records).isEmpty();
        assertThat(queued).isEmpty();
    }

    @Test
    void returnsAJobBeforeTheChatbotAnswersThenPendingBecomesDone_AC_010_4_BUG_339() {
        List<ChatbotTurnRequest> history = List.of(new ChatbotTurnRequest("VPN fails", "Check the client [1]"));
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);
        var service = service(Duration.ofSeconds(60));

        var job = service.start(CALLER, new JiraAppQuestionRequest("Still failing", history));

        assertThat(job.jobId()).isNotNull();
        assertThat(service.result(CALLER, job.jobId()))
                .isEqualTo(new JiraAppQuestionJobResult(JiraAppQuestionJobResult.Status.PENDING, null));
        assertThat(pending.get()).isNotCompleted();
        verifyNoInteractions(answers);
        runQueuedTasks();
        assertThat(service.result(CALLER, job.jobId()))
                .isEqualTo(new JiraAppQuestionJobResult(JiraAppQuestionJobResult.Status.DONE, ANSWER));
        verify(answers).askForProject(PROJECT, Optional.empty(), new ChatbotQuestionRequest("Still failing", history));
    }

    @Test
    void givesALinkedAskerTheWikiOfTheirTsolveUser_AC_010_4() {
        TSolveUser linked = new TSolveUser(UUID.randomUUID(), "an@t-solve.vn", "An", java.util.Set.of());
        AtomicReference<UUID> boundAtLookup = new AtomicReference<>();
        when(askers.find("cloud-1", "jira-1")).thenAnswer(call -> {
            boundAtLookup.set(tenantScope.currentWorkspaceId());
            return Optional.of(linked);
        });
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);

        service(Duration.ofSeconds(5)).start(CALLER, new JiraAppQuestionRequest("VPN fails", List.of()));
        queued.remove().run();

        verify(answers).askForProject(PROJECT, Optional.of(linked), new ChatbotQuestionRequest("VPN fails", List.of()));
        assertThat(boundAtLookup).hasValue(WORKSPACE);
    }

    @Test
    void sendsNoHistoryAsAnEmptyHistory_AC_010_4() {
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);

        service(Duration.ofSeconds(5)).start(CALLER, new JiraAppQuestionRequest("VPN fails", null));
        queued.remove().run();

        verify(answers).askForProject(PROJECT, Optional.empty(), new ChatbotQuestionRequest("VPN fails", List.of()));
    }

    @Test
    void refusesASiteOfNoWorkspaceWithMsg124WithoutCallingTheAi_AC_010_11() {
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service(Duration.ofSeconds(5)).start(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(JiraSiteNotConnectedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("MSG124");
                });
        verifyNoInteractions(projects, answers);
        assertThat(jobs.records).isEmpty();
        assertThat(queued).isEmpty();
    }

    @Test
    void refusesAProjectThatIsNotAnActiveTsolveProjectsWithMsg08_AC_010_11() {
        when(projects.findActivePublicIdByJiraProjectKey("DP")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service(Duration.ofSeconds(5)).start(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(JiraIssueAccessDeniedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("MSG08");
                });
        verifyNoInteractions(answers);
        assertThat(jobs.records).isEmpty();
        assertThat(queued).isEmpty();
    }

    @Test
    void refusesAFitWithoutAProjectWithoutCallingTheAi_AC_010_11_AC_010_13() {
        JiraAppPrincipal issuePanel = new JiraAppPrincipal("cloud-1", "jira-1", null);

        assertThatThrownBy(() ->
                        service(Duration.ofSeconds(5)).start(issuePanel, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        verifyNoInteractions(projects, answers);
        assertThat(jobs.records).isEmpty();
        assertThat(queued).isEmpty();
    }

    @Test
    void refusesAFitWithoutAJiraUserWithoutCallingTheAi_AC_010_11_AC_010_14() {
        JiraAppPrincipal anonymous = new JiraAppPrincipal("cloud-1", null, null, "DP");
        JiraAppPrincipal blank = new JiraAppPrincipal("cloud-1", " ", null, "DP");
        var service = service(Duration.ofSeconds(5));

        assertThatThrownBy(() -> service.start(anonymous, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThatThrownBy(
                        () -> service.rate(blank, UUID.randomUUID(), new JiraAppRatingRequest(ChatbotRating.HELPFUL)))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThat(service.eligibility(anonymous).status()).isEqualTo(Status.PROJECT_NOT_CONNECTED);
        verifyNoInteractions(projects, answers);
        assertThat(jobs.records).isEmpty();
        assertThat(queued).isEmpty();
    }

    @Test
    void recordsFailedWhenTheChatbotThrows_AC_010_4_AC_010_8() {
        when(answers.askForProject(any(), any(), any())).thenThrow(new AiUnavailableException());
        var service = service(Duration.ofSeconds(60));
        var job = service.start(CALLER, new JiraAppQuestionRequest("q", List.of()));

        runQueuedTasks();

        assertThat(service.result(CALLER, job.jobId()))
                .isEqualTo(new JiraAppQuestionJobResult(JiraAppQuestionJobResult.Status.FAILED, null));
    }

    @Test
    void recordsFailedOnTimeoutAndIgnoresALateAnswer_AC_010_4_AC_010_8() {
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);
        var service = service(Duration.ofSeconds(60));
        var job = service.start(CALLER, new JiraAppQuestionRequest("q", List.of()));

        pending.get().completeExceptionally(new TimeoutException());
        runQueuedTasks();

        assertThat(service.result(CALLER, job.jobId()))
                .isEqualTo(new JiraAppQuestionJobResult(JiraAppQuestionJobResult.Status.FAILED, null));
        assertThat(pending.get().complete(ANSWER)).isFalse();
        verifyNoInteractions(answers);
        assertThat(service.result(CALLER, job.jobId()).status()).isEqualTo(JiraAppQuestionJobResult.Status.FAILED);
    }

    @Test
    void reportsTheThreeStatuses_AC_010_14() {
        var service = service(Duration.ofSeconds(5));
        assertThat(service.eligibility(CALLER).status()).isEqualTo(Status.CONNECTED);
        assertThat(service.eligibility(new JiraAppPrincipal("cloud-1", "jira-1", null))
                        .status())
                .isEqualTo(Status.PROJECT_NOT_CONNECTED);
        when(projects.findActivePublicIdByJiraProjectKey("DP")).thenReturn(Optional.empty());
        assertThat(service.eligibility(CALLER).status()).isEqualTo(Status.PROJECT_NOT_CONNECTED);
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.empty());
        assertThat(service.eligibility(CALLER).status()).isEqualTo(Status.SITE_NOT_CONNECTED);
        verifyNoInteractions(answers);
        assertThat(jobs.records).isEmpty();
        assertThat(queued).isEmpty();
    }

    @Test
    void findsTheProjectInTheWorkspaceOfTheSite_BUG_318() {
        AtomicReference<UUID> boundAtLookup = new AtomicReference<>();
        when(projects.findActivePublicIdByJiraProjectKey("DP")).thenAnswer(call -> {
            boundAtLookup.set(tenantScope.currentWorkspaceId());
            return Optional.of(PROJECT);
        });

        assertThat(service(Duration.ofSeconds(5)).eligibility(CALLER).status()).isEqualTo(Status.CONNECTED);
        assertThat(boundAtLookup).hasValue(WORKSPACE);
    }

    @Test
    void asksTheChatbotInTheWorkspaceOfTheSite_BUG_318() {
        AtomicReference<UUID> boundAtAnswer = new AtomicReference<>();
        when(answers.askForProject(any(), any(), any())).thenAnswer(call -> {
            boundAtAnswer.set(tenantScope.currentWorkspaceId());
            return ANSWER;
        });

        service(Duration.ofSeconds(5)).start(CALLER, new JiraAppQuestionRequest("q", List.of()));
        queued.remove().run();

        assertThat(boundAtAnswer).hasValue(WORKSPACE);
    }

    @Test
    void ratesTheAnswerInTheProjectAndWorkspaceOfTheFit_AC_010_15() {
        UUID answerId = UUID.randomUUID();
        AtomicReference<UUID> boundAtRating = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(call -> {
                    boundAtRating.set(tenantScope.currentWorkspaceId());
                    return null;
                })
                .when(answers)
                .rateForProject(PROJECT, answerId, ChatbotRating.HELPFUL);

        service(Duration.ofSeconds(5)).rate(CALLER, answerId, new JiraAppRatingRequest(ChatbotRating.HELPFUL));

        verify(answers).rateForProject(PROJECT, answerId, ChatbotRating.HELPFUL);
        assertThat(boundAtRating).hasValue(WORKSPACE);
    }

    @Test
    void passesOnTheNotFoundOfAnAnswerOfAnotherProject_AC_010_15() {
        UUID answerId = UUID.randomUUID();
        doThrow(new ChatbotAnswerNotFoundException())
                .when(answers)
                .rateForProject(PROJECT, answerId, ChatbotRating.NOT_HELPFUL);

        assertThatThrownBy(() -> service(Duration.ofSeconds(5))
                        .rate(CALLER, answerId, new JiraAppRatingRequest(ChatbotRating.NOT_HELPFUL)))
                .isInstanceOf(ChatbotAnswerNotFoundException.class);
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(Duration.ofSeconds(5))
                        .rate(CALLER, answerId, new JiraAppRatingRequest(ChatbotRating.NOT_HELPFUL)))
                .isInstanceOf(JiraSiteNotConnectedException.class);
    }

    @Test
    void refusesANonPositiveDeadline_AC_010_4() {
        assertThatThrownBy(() -> service(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hidesJobsOfAnotherAccountSiteOrProject_AC_010_16() {
        var service = service(Duration.ofSeconds(60));
        var job = service.start(CALLER, new JiraAppQuestionRequest("q", List.of()));
        queued.remove().run();
        for (var caller : List.of(
                new JiraAppPrincipal("cloud-2", "jira-1", null, "DP"),
                new JiraAppPrincipal("cloud-1", "jira-2", null, "DP"),
                new JiraAppPrincipal("cloud-1", "jira-1", null, "OTHER"))) {
            assertThatThrownBy(() -> service.result(caller, job.jobId()))
                    .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode())
                            .isEqualTo("MSG06"));
        }
    }

    @Test
    void hidesUnknownAndExpiredJobs_AC_010_16() {
        var service = service(Duration.ofSeconds(60));
        assertThatThrownBy(() -> service.result(CALLER, UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("MSG06"));
        var job = service.start(CALLER, new JiraAppQuestionRequest("q", List.of()));
        jobs.records.clear();
        assertThatThrownBy(() -> service.result(CALLER, job.jobId()))
                .isInstanceOfSatisfying(JiraAppQuestionJobNotFoundException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG06"));
    }

    @Test
    void logsOnlyJobIdAndErrorTypeWhenRedisUpdateFails_AC_010_12_AC_010_17() {
        jobs.failUpdates = true;
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);
        var service = service(Duration.ofSeconds(60));
        var job = service.start(CALLER, new JiraAppQuestionRequest("private question", List.of()));
        runQueuedTasks();
        assertOnlyLog(job.jobId(), "IllegalStateException");
        assertThat(service.result(CALLER, job.jobId()).status()).isEqualTo(JiraAppQuestionJobResult.Status.PENDING);
    }

    private static class InMemoryAskJobStore extends RedisAskJobStore {
        final Map<UUID, Job> records = new HashMap<>();
        int created;
        boolean failUpdates;
        boolean failCreates;
        boolean failReads;
        UUID lastCreateId;

        InMemoryAskJobStore() {
            super(mock(StringRedisTemplate.class), new ObjectMapper());
        }

        @Override
        void create(UUID id, Job job) {
            lastCreateId = id;
            if (failCreates) {
                throw new RedisConnectionFailureException("private Redis error");
            }
            records.put(id, job);
            created++;
        }

        @Override
        Optional<Job> find(UUID id) {
            if (failReads) {
                throw new RedisConnectionFailureException("private Redis error");
            }
            return Optional.ofNullable(records.get(id));
        }

        @Override
        void update(UUID id, Job job) {
            if (failUpdates) {
                throw new IllegalStateException("private Redis error");
            }
            records.computeIfPresent(id, (key, previous) -> job);
        }

        @Override
        void delete(UUID id) {
            records.remove(id);
        }
    }

    private JiraAppQuestionServiceImpl service(Duration timeout) {
        return new JiraAppQuestionServiceImpl(
                workspaces, tenantScope, projects, askers, answers, jobs, executor, timeout, (future, deadline) -> {
                    assertThat(deadline).isEqualTo(timeout);
                    pending.set(future);
                });
    }
}
