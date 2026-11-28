package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.tsolve.ai.dto.IssueContent;
import vn.tsolve.ai.dto.SolutionDraft;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.ai.service.SolutionAiService;
import vn.tsolve.jiraapp.service.impl.SolutionFormAiImpl.Mode;
import vn.tsolve.ticket.dto.SolutionSections;

class SolutionFormAiImplTest {

    private static final vn.tsolve.ai.dto.SolutionSections AI_SECTIONS =
            new vn.tsolve.ai.dto.SolutionSections("Drops hourly", "Old client", "Update to 5.2", "", "Laptops");

    private final SolutionAiService ai = mock(SolutionAiService.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void rewritesTheFiveSections_AC_007_1() {
        when(ai.rewrite(new vn.tsolve.ai.dto.SolutionSections("drops", "old", "update", "", "laptops")))
                .thenReturn(AI_SECTIONS);

        SolutionSections rewritten =
                rewrite(service(Mode.AI), new SolutionSections("drops", "old", "update", "", "laptops"));

        assertThat(rewritten)
                .isEqualTo(new SolutionSections("Drops hourly", "Old client", "Update to 5.2", "", "Laptops"));
    }

    @Test
    void answersMsg68WhenTheRewriteFails_AC_007_4() {
        when(ai.rewrite(any())).thenThrow(new AiRewriteUnavailableException());

        assertThatThrownBy(() -> rewrite(service(Mode.AI), new SolutionSections("a", "b", "c", "", "d")))
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AiRewriteUnavailableException.class);
    }

    @Test
    void failsWhenRewriteMissesTheDeadline_AC_007_5() {
        java.util.Queue<Runnable> work = new java.util.ArrayDeque<>();
        var service = new SolutionFormAiImpl(
                ai,
                Mode.AI,
                work::add,
                Runnable::run,
                Duration.ofSeconds(60),
                (future, limit) -> future.completeExceptionally(new TimeoutException()));
        CompletableFuture<SolutionSections> result = new CompletableFuture<>();
        service.startRewrite(new SolutionSections("a", "b", "c", "", "d"), (sections, error) -> {
            if (error != null) result.completeExceptionally(error);
            else result.complete(sections);
        });
        while (!work.isEmpty()) work.remove().run();
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void servesCannedAnswersWithoutTheAiServerWhenStubbedAvailable() {
        SolutionFormAiImpl stub = service(Mode.AVAILABLE);

        assertThat(rewrite(stub, new SolutionSections("drops", "old", "update", "", "laptops"))
                        .symptoms())
                .isEqualTo("drops.");
        verifyNoInteractions(ai);
    }

    @Test
    void behavesAsAnUnavailableAiWhenStubbedUnavailable_AC_007_4() {
        SolutionFormAiImpl stub = service(Mode.UNAVAILABLE);

        assertThatThrownBy(() -> rewrite(stub, new SolutionSections("a", "b", "c", "", "d")))
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AiRewriteUnavailableException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void readsTheStubSetting() {
        assertThat(Mode.of("")).isEqualTo(Mode.AI);
        assertThat(Mode.of(" Available ")).isEqualTo(Mode.AVAILABLE);
        assertThat(Mode.of("unavailable")).isEqualTo(Mode.UNAVAILABLE);
        assertThatThrownBy(() -> Mode.of("maybe")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rewritePoolHasAsManyWaitingSlotsAsWorkersAndAbortsWhenFull_AC_007_8() {
        var pool = JiraAppJobs.pool(4, "JIRA_APP_REWRITE_THREADS", "jira-app-rewrite-");
        try {
            assertThat(pool.getCorePoolSize()).isEqualTo(4);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(4);
            assertThat(pool.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
            var thread = pool.getThreadFactory().newThread(() -> {});
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).startsWith("jira-app-rewrite-");
        } finally {
            pool.shutdownNow();
        }
        assertThatThrownBy(() -> JiraAppJobs.pool(0, "JIRA_APP_REWRITE_THREADS", "jira-app-rewrite-"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JIRA_APP_REWRITE_THREADS must be positive");
    }

    @Test
    void stopsTheRewritePool_AC_007_8() {
        ExecutorService rewrites = mock(ExecutorService.class);
        ExecutorService completions = mock(ExecutorService.class);
        var service = new SolutionFormAiImpl(
                ai, Mode.AI, rewrites, completions, Duration.ofSeconds(60), (future, limit) -> {});

        service.stop();

        verify(rewrites).shutdownNow();
        verifyNoInteractions(completions);
    }

    @Test
    void passesTheAiDraftOfTheIssueAndItsComments_AC_005_11() {
        when(ai.draft(new IssueContent("VPN drops", "After sleep", List.of("Restarted"))))
                .thenReturn(new SolutionDraft(
                        new vn.tsolve.ai.dto.SolutionSections("", "Old client", "Update to 5.2", "", ""), true));
        List<SolutionSections> applied = new java.util.ArrayList<>();

        inline(Mode.AI, (future, limit) -> {})
                .startDraft("VPN drops", "After sleep", () -> List.of("Restarted"), applied::add);

        assertThat(applied).containsExactly(new SolutionSections("", "Old client", "Update to 5.2", "", ""));
    }

    @Test
    void passesNothingForAFallbackAFailureOrATimeout_AC_005_12() {
        List<SolutionSections> applied = new java.util.ArrayList<>();
        when(ai.draft(any())).thenReturn(new SolutionDraft(AI_SECTIONS, false));
        inline(Mode.AI, (future, limit) -> {}).startDraft("S", "D", List::of, applied::add);

        when(ai.draft(any())).thenThrow(new IllegalStateException("down"));
        inline(Mode.AI, (future, limit) -> {}).startDraft("S", "D", List::of, applied::add);

        java.util.Queue<Runnable> work = new java.util.ArrayDeque<>();
        new SolutionFormAiImpl(
                        ai,
                        Mode.AI,
                        work::add,
                        Runnable::run,
                        Duration.ofSeconds(60),
                        (future, limit) -> future.completeExceptionally(new TimeoutException()))
                .startDraft("S", "D", List::of, applied::add);
        while (!work.isEmpty()) work.remove().run();

        assertThat(applied).isEmpty();
    }

    @Test
    void draftsOnlyWithTheRealAi_AC_005_11() {
        List<SolutionSections> applied = new java.util.ArrayList<>();
        for (Mode stub : List.of(Mode.AVAILABLE, Mode.UNAVAILABLE)) {
            inline(stub, (future, limit) -> {}).startDraft("S", "D", List::of, applied::add);
        }

        assertThat(applied).isEmpty();
        verifyNoInteractions(ai);
    }

    private SolutionFormAiImpl inline(
            Mode mode, java.util.function.BiConsumer<CompletableFuture<SolutionSections>, Duration> deadline) {
        return new SolutionFormAiImpl(ai, mode, Runnable::run, Runnable::run, Duration.ofSeconds(60), deadline);
    }

    private static SolutionSections rewrite(SolutionFormAiImpl service, SolutionSections input) {
        CompletableFuture<SolutionSections> result = new CompletableFuture<>();
        service.startRewrite(input, (sections, error) -> {
            if (error != null) result.completeExceptionally(error);
            else result.complete(sections);
        });
        return result.join();
    }

    private SolutionFormAiImpl service(Mode mode) {
        return new SolutionFormAiImpl(
                ai,
                mode,
                executor,
                Runnable::run,
                Duration.ofSeconds(60),
                (future, limit) -> future.orTimeout(limit.toNanos(), TimeUnit.NANOSECONDS));
    }
}
