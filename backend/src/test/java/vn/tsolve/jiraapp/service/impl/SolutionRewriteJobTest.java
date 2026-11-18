package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import vn.tsolve.ai.dto.SolutionDraft;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.ai.service.SolutionAiService;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.jiraapp.dto.JiraAppJobStatus;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;
import vn.tsolve.jiraapp.service.JiraIssueAccess;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.ticket.service.SolutionFormTicketService;

class SolutionRewriteJobTest {
    private static final JiraAppPrincipal CALLER = new JiraAppPrincipal("cloud", "account", null);
    private static final SolutionSections INPUT =
            new SolutionSections("private symptoms", "cause", "steps", "", "scope");
    private static final SolutionSections OUTPUT = new SolutionSections("Rewritten", "Cause", "Steps", "", "Scope");
    private final JiraIssueAccess access = mock(JiraIssueAccess.class);
    private final SolutionAiService ai = mock(SolutionAiService.class);
    private final FakeStore jobs = new FakeStore();
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private final Queue<Runnable> completions = new ArrayDeque<>();
    private final AtomicReference<CompletableFuture<SolutionSections>> future = new AtomicReference<>();
    private boolean reject;
    private boolean rejectCompletions;
    private int rewriteCapacity = Integer.MAX_VALUE;
    private SolutionFormAiImpl formAi;
    private final Executor executor = task -> {
        if (reject || queue.size() >= rewriteCapacity) throw new RejectedExecutionException("private pool message");
        queue.add(task);
    };
    // Production falls back to a new virtual thread; inline here keeps the test synchronous.
    private final Executor completionExecutor = JiraAppJobCompletions.withFallback(
            task -> {
                if (rejectCompletions) throw new RejectedExecutionException("private completion message");
                completions.add(task);
            },
            Runnable::run);

    @BeforeEach
    void allowAccess() {
        when(access.asUser(eq(CALLER), any())).thenAnswer(call -> {
            Function<TSolveUser, Object> work = call.getArgument(1);
            return work.apply(mock(TSolveUser.class));
        });
    }

    @Test
    void returnsBeforeAiThenPendingAndDone_AC_007_5_AC_012_7_BUG_345() {
        when(ai.rewrite(any()))
                .thenReturn(new vn.tsolve.ai.dto.SolutionSections("Rewritten", "Cause", "Steps", "", "Scope"));
        var service = service(SolutionFormAiImpl.Mode.AI);
        var job = service.startRewrite(CALLER, INPUT);
        verifyNoInteractions(ai);
        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.PENDING);
        assertThat(service.rewriteResult(CALLER, job.jobId()).sections()).isNull();
        queue.remove().run();
        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.PENDING);
        assertThat(queue).isEmpty();
        assertThat(completions).hasSize(1);
        completions.remove().run();
        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.DONE);
        assertThat(service.rewriteResult(CALLER, job.jobId()).sections()).isEqualTo(OUTPUT);
    }

    @Test
    void failsOnAiErrorAndLogsOnlyIdAndUnwrappedType_AC_007_5_AC_007_7() {
        when(ai.rewrite(any())).thenThrow(new CompletionException(new IllegalArgumentException("private symptoms")));
        Logger logger = (Logger) LoggerFactory.getLogger(JiraAppSolutionServiceImpl.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            var service = service(SolutionFormAiImpl.Mode.AI);
            var job = service.startRewrite(CALLER, INPUT);
            drain();
            assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.FAILED);
            assertThat(service.rewriteResult(CALLER, job.jobId()).sections()).isNull();
            assertThat(logs.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .contains(job.jobId().toString(), "IllegalArgumentException")
                        .doesNotContain("private", "CompletionException");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void timeoutIncludesQueueTimeAndCannotBecomeDoneLater_AC_007_5() {
        var service = service(SolutionFormAiImpl.Mode.AI);
        var job = service.startRewrite(CALLER, INPUT);
        future.get().completeExceptionally(new TimeoutException("private timeout"));
        drain();
        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.FAILED);
        assertThat(service.rewriteResult(CALLER, job.jobId()).sections()).isNull();
        verifyNoInteractions(ai);
        assertThat(future.get().complete(OUTPUT)).isFalse();
    }

    @Test
    void lateAiAnswerCannotOverwriteRunningJobTimeout_AC_007_5() {
        when(ai.rewrite(any())).thenAnswer(call -> {
            future.get().completeExceptionally(new TimeoutException());
            return new vn.tsolve.ai.dto.SolutionSections("Rewritten", "Cause", "Steps", "", "Scope");
        });
        var service = service(SolutionFormAiImpl.Mode.AI);
        var job = service.startRewrite(CALLER, INPUT);
        drain();
        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.FAILED);
        assertThat(service.rewriteResult(CALLER, job.jobId()).sections()).isNull();
    }

    @Test
    void stubFinishesWithCannedSections_AC_007_5() {
        var service = service(SolutionFormAiImpl.Mode.AVAILABLE);
        var job = service.startRewrite(CALLER, INPUT);
        drain();
        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.DONE);
        assertThat(service.rewriteResult(CALLER, job.jobId()).sections().symptoms())
                .isEqualTo("private symptoms.");
        verifyNoInteractions(ai);
    }

    @Test
    void hidesOtherAccountSiteUnknownExpiredButAllowsAnotherProject_AC_007_6() {
        var service = service(SolutionFormAiImpl.Mode.AVAILABLE);
        var job = service.startRewrite(CALLER, INPUT);
        for (var caller :
                List.of(new JiraAppPrincipal("other", "account", null), new JiraAppPrincipal("cloud", "other", null))) {
            assertNotFound(() -> service.rewriteResult(caller, job.jobId()));
        }
        assertNotFound(() -> service.rewriteResult(CALLER, UUID.randomUUID()));
        assertThat(service.rewriteResult(new JiraAppPrincipal("cloud", "account", null, "OTHER"), job.jobId())
                        .status())
                .isEqualTo(JiraAppJobStatus.PENDING);
        jobs.records.clear();
        assertNotFound(() -> service.rewriteResult(CALLER, job.jobId()));
    }

    @Test
    void fullPoolRemovesRecord_AC_007_8() {
        reject = true;
        var service = service(SolutionFormAiImpl.Mode.AI);
        assertUnavailable(() -> service.startRewrite(CALLER, INPUT));
        assertThat(jobs.records).isEmpty();
        verifyNoInteractions(ai);
    }

    @Test
    void fullRewritePoolDoesNotMakeTheDraftBusy_AC_002_2() {
        rewriteCapacity = 2;
        when(ai.draft(any()))
                .thenReturn(new SolutionDraft(
                        new vn.tsolve.ai.dto.SolutionSections("Rewritten", "Cause", "Steps", "", "Scope"), true));
        var service = service(SolutionFormAiImpl.Mode.AI);
        service.startRewrite(CALLER, INPUT);
        service.startRewrite(CALLER, INPUT);

        var draft = formAi.draft("summary", "description");
        assertThat(draft.aiDraft()).isTrue();
        assertThat(draft.sections()).isEqualTo(OUTPUT);
        assertThat(queue).hasSize(2);
    }

    @Test
    void fullRewritePoolRefusesAnotherJobWhileTheDraftPoolIsFree_AC_007_8() {
        rewriteCapacity = 2;
        when(ai.draft(any()))
                .thenReturn(new SolutionDraft(
                        new vn.tsolve.ai.dto.SolutionSections("Rewritten", "Cause", "Steps", "", "Scope"), true));
        var service = service(SolutionFormAiImpl.Mode.AI);
        service.startRewrite(CALLER, INPUT);
        service.startRewrite(CALLER, INPUT);

        assertUnavailable(() -> service.startRewrite(CALLER, INPUT));
        assertThat(jobs.records).hasSize(2);
        assertThat(queue).hasSize(2);
        verifyNoInteractions(ai);
        assertThat(formAi.draft("summary", "description").aiDraft()).isTrue();
    }

    @Test
    void redisCreateFailureCleansUpEvenIfWriteReachedRedis_AC_007_8() {
        jobs.failCreates = true;
        var service = service(SolutionFormAiImpl.Mode.AI);
        assertUnavailable(() -> service.startRewrite(CALLER, INPUT));
        assertThat(jobs.records).isEmpty();
        assertThat(queue).isEmpty();
        verifyNoInteractions(ai);
    }

    @Test
    void redisReadFailureIsUnavailable_AC_007_6_AC_007_8() {
        jobs.failReads = true;
        assertUnavailable(() -> service(SolutionFormAiImpl.Mode.AI).rewriteResult(CALLER, UUID.randomUUID()));
    }

    @Test
    void callbackFallsBackInlineWhenCompletionPoolRejects_AC_007_5_AC_007_8() {
        when(ai.rewrite(any()))
                .thenReturn(new vn.tsolve.ai.dto.SolutionSections("Rewritten", "Cause", "Steps", "", "Scope"));
        var service = service(SolutionFormAiImpl.Mode.AI);
        var job = service.startRewrite(CALLER, INPUT);
        rejectCompletions = true;
        drain();
        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.DONE);
        assertThat(completions).isEmpty();
    }

    @Test
    void timeoutFallsBackInlineWhenCompletionPoolRejects_AC_007_5_AC_007_8() {
        var service = service(SolutionFormAiImpl.Mode.AI);
        var job = service.startRewrite(CALLER, INPUT);
        rejectCompletions = true;

        future.get().completeExceptionally(new TimeoutException());

        assertThat(service.rewriteResult(CALLER, job.jobId()).status()).isEqualTo(JiraAppJobStatus.FAILED);
        assertThat(completions).isEmpty();
        verifyNoInteractions(ai);
    }

    @Test
    void completionDoesNotWaitForQueuedRewrites_AC_007_5() {
        var service = service(SolutionFormAiImpl.Mode.AVAILABLE);
        var first = service.startRewrite(CALLER, INPUT);
        var second = service.startRewrite(CALLER, INPUT);

        queue.remove().run();
        assertThat(queue).hasSize(1);
        completions.remove().run();

        assertThat(service.rewriteResult(CALLER, first.jobId()).status()).isEqualTo(JiraAppJobStatus.DONE);
        assertThat(service.rewriteResult(CALLER, second.jobId()).status()).isEqualTo(JiraAppJobStatus.PENDING);
        assertThat(queue).hasSize(1);
    }

    @Test
    void refusesAccessSynchronouslyBeforeCreatingJob_AC_007_5() {
        when(access.asUser(eq(CALLER), any())).thenThrow(new JiraIssueAccessDeniedException());
        var service = service(SolutionFormAiImpl.Mode.AI);
        assertThatThrownBy(() -> service.startRewrite(CALLER, INPUT))
                .isInstanceOf(JiraIssueAccessDeniedException.class);
        assertThat(jobs.records).isEmpty();
        assertThat(queue).isEmpty();
        verifyNoInteractions(ai);
    }

    private JiraAppSolutionServiceImpl service(SolutionFormAiImpl.Mode mode) {
        formAi = new SolutionFormAiImpl(
                ai,
                mode,
                Runnable::run,
                executor,
                completionExecutor,
                Duration.ofSeconds(18),
                Duration.ofSeconds(60),
                (pending, limit) -> {
                    assertThat(limit).isEqualTo(Duration.ofSeconds(60));
                    future.set(pending);
                });
        return new JiraAppSolutionServiceImpl(
                access, mock(SolutionFormTicketService.class), mock(SolutionDraftService.class), formAi, jobs);
    }

    private void drain() {
        while (!queue.isEmpty()) queue.remove().run();
        while (!completions.isEmpty()) completions.remove().run();
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AiRewriteUnavailableException.class, e -> {
            assertThat(e.getCode()).isEqualTo("MSG68");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode())
                .isEqualTo("MSG68"));
    }

    private static class FakeStore extends RedisAskJobStore {
        final Map<String, Job<?>> records = new HashMap<>();
        boolean failCreates;
        boolean failReads;

        FakeStore() {
            super(mock(StringRedisTemplate.class), new ObjectMapper());
        }

        @Override
        <T> void create(String prefix, UUID id, Job<T> job) {
            records.put(prefix + id, job);
            if (failCreates) throw new RedisConnectionFailureException("private error");
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> Optional<Job<T>> find(String prefix, UUID id, Class<T> payloadType) {
            if (failReads) throw new RedisConnectionFailureException("private error");
            return Optional.ofNullable((Job<T>) records.get(prefix + id));
        }

        @Override
        <T> void update(String prefix, UUID id, Job<T> job) {
            records.computeIfPresent(prefix + id, (key, old) -> job);
        }

        @Override
        void delete(String prefix, UUID id) {
            records.remove(prefix + id);
        }
    }
}
