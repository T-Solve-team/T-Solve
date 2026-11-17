package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.signin.port.LocalTenantScope;
import vn.tsolve.jiraapp.dto.JiraAppQuestionRequest;
import vn.tsolve.jiraapp.dto.JiraAppRatingRequest;
import vn.tsolve.jiraapp.dto.JiraAskerEligibility.Status;
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void registerTheSiteAndProject() {
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.of(WORKSPACE));
        when(projects.findActivePublicIdByJiraProjectKey("DP")).thenReturn(Optional.of(PROJECT));
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
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
    void answersMsg06AtOnceWhenItsPoolIsFull_BUG_183() throws Exception {
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>());
        CountDownLatch release = new CountDownLatch(1);
        when(answers.askForProject(any(), any(), any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return ANSWER;
        });
        JiraAppQuestionServiceImpl service = new JiraAppQuestionServiceImpl(
                workspaces, tenantScope, projects, askers, answers, one, Duration.ofSeconds(5));
        try {
            CompletableFuture<ChatbotAnswerResponse> first = CompletableFuture.supplyAsync(
                    () -> service.ask(CALLER, new JiraAppQuestionRequest("q1", List.of())));
            long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (one.getActiveCount() < 1 && System.nanoTime() < waitUntil) {
                Thread.onSpinWait();
            }

            long start = System.nanoTime();
            assertThatThrownBy(() -> service.ask(CALLER, new JiraAppQuestionRequest("q2", List.of())))
                    .isInstanceOf(AiUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(ANSWER);
        } finally {
            release.countDown();
            one.shutdownNow();
        }
    }

    @Test
    void returnsTheChatbotAnswerForTheProjectOfAnyJiraUser_AC_010_4() {
        List<ChatbotTurnRequest> history = List.of(new ChatbotTurnRequest("VPN fails", "Check the client [1]"));
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);

        ChatbotAnswerResponse answer =
                service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("Still failing", history));

        assertThat(answer).isSameAs(ANSWER);
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

        service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("VPN fails", List.of()));

        verify(answers).askForProject(PROJECT, Optional.of(linked), new ChatbotQuestionRequest("VPN fails", List.of()));
        assertThat(boundAtLookup).hasValue(WORKSPACE);
    }

    @Test
    void sendsNoHistoryAsAnEmptyHistory_AC_010_4() {
        when(answers.askForProject(any(), any(), any())).thenReturn(ANSWER);

        service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("VPN fails", null));

        verify(answers).askForProject(PROJECT, Optional.empty(), new ChatbotQuestionRequest("VPN fails", List.of()));
    }

    @Test
    void refusesASiteOfNoWorkspaceWithMsg124WithoutCallingTheAi_AC_010_11() {
        when(workspaces.workspaceOf("cloud-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(JiraSiteNotConnectedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("MSG124");
                });
        verifyNoInteractions(projects, answers);
    }

    @Test
    void refusesAProjectThatIsNotAnActiveTsolveProjectsWithMsg08_AC_010_11() {
        when(projects.findActivePublicIdByJiraProjectKey("DP")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(JiraIssueAccessDeniedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("MSG08");
                });
        verifyNoInteractions(answers);
    }

    @Test
    void refusesAFitWithoutAProjectWithoutCallingTheAi_AC_010_11_AC_010_13() {
        JiraAppPrincipal issuePanel = new JiraAppPrincipal("cloud-1", "jira-1", null);

        assertThatThrownBy(() ->
                        service(Duration.ofSeconds(5)).ask(issuePanel, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        verifyNoInteractions(projects, answers);
    }

    @Test
    void refusesAFitWithoutAJiraUserWithoutCallingTheAi_AC_010_11_AC_010_14() {
        JiraAppPrincipal anonymous = new JiraAppPrincipal("cloud-1", null, null, "DP");
        JiraAppPrincipal blank = new JiraAppPrincipal("cloud-1", " ", null, "DP");
        var service = service(Duration.ofSeconds(5));

        assertThatThrownBy(() -> service.ask(anonymous, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThatThrownBy(
                        () -> service.rate(blank, UUID.randomUUID(), new JiraAppRatingRequest(ChatbotRating.HELPFUL)))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThat(service.eligibility(anonymous).status()).isEqualTo(Status.PROJECT_NOT_CONNECTED);
        verifyNoInteractions(projects, answers);
    }

    @Test
    void answersMsg06WhenTheAiIsUnavailable_AC_010_8() {
        when(answers.askForProject(any(), any(), any())).thenThrow(new AiUnavailableException());

        assertThatThrownBy(() -> service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("MSG06");
                });
    }

    @Test
    void answersMsg06WhenTheChatbotMissesTheDeadline_AC_010_8() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(answers.askForProject(any(), any(), any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return ANSWER;
        });

        long start = System.nanoTime();
        assertThatThrownBy(
                        () -> service(Duration.ofMillis(100)).ask(CALLER, new JiraAppQuestionRequest("q", List.of())))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        release.countDown();
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

        service(Duration.ofSeconds(5)).ask(CALLER, new JiraAppQuestionRequest("q", List.of()));

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
    void refusesANonPositiveDeadline() {
        assertThatThrownBy(() -> service(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private JiraAppQuestionServiceImpl service(Duration timeout) {
        return new JiraAppQuestionServiceImpl(workspaces, tenantScope, projects, askers, answers, executor, timeout);
    }
}
