package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
                service(Mode.AI).rewrite(new SolutionSections("drops", "old", "update", "", "laptops"));

        assertThat(rewritten)
                .isEqualTo(new SolutionSections("Drops hourly", "Old client", "Update to 5.2", "", "Laptops"));
    }

    @Test
    void answersMsg68WhenTheRewriteFails_AC_007_4() {
        when(ai.rewrite(any())).thenThrow(new AiRewriteUnavailableException());

        assertThatThrownBy(() -> service(Mode.AI).rewrite(new SolutionSections("a", "b", "c", "", "d")))
                .isInstanceOfSatisfying(AiRewriteUnavailableException.class, e -> assertThat(e.getCode())
                        .isEqualTo("MSG68"));
    }

    @Test
    void answersMsg68WhenTheRewriteMissesTheDeadline_AC_007_4() {
        CountDownLatch release = new CountDownLatch(1);
        when(ai.rewrite(any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return AI_SECTIONS;
        });

        assertThatThrownBy(() ->
                        service(Mode.AI, Duration.ofMillis(100)).rewrite(new SolutionSections("a", "b", "c", "", "d")))
                .isInstanceOf(AiRewriteUnavailableException.class);
        release.countDown();
    }

    @Test
    void servesCannedAnswersWithoutTheAiServerWhenStubbedAvailable() {
        SolutionFormAiImpl stub = service(Mode.AVAILABLE);

        assertThat(stub.rewrite(new SolutionSections("drops", "old", "update", "", "laptops"))
                        .symptoms())
                .isEqualTo("drops.");
        verifyNoInteractions(ai);
    }

    @Test
    void behavesAsAnUnavailableAiWhenStubbedUnavailable_AC_007_4() {
        SolutionFormAiImpl stub = service(Mode.UNAVAILABLE);

        assertThatThrownBy(() -> stub.rewrite(new SolutionSections("a", "b", "c", "", "d")))
                .isInstanceOf(AiRewriteUnavailableException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void readsTheStubSetting() {
        assertThat(Mode.of("")).isEqualTo(Mode.AI);
        assertThat(Mode.of(" Available ")).isEqualTo(Mode.AVAILABLE);
        assertThat(Mode.of("unavailable")).isEqualTo(Mode.UNAVAILABLE);
        assertThatThrownBy(() -> Mode.of("maybe")).isInstanceOf(IllegalArgumentException.class);
    }

    private SolutionFormAiImpl service(Mode mode) {
        return service(mode, Duration.ofSeconds(5));
    }

    private SolutionFormAiImpl service(Mode mode, Duration timeout) {
        return new SolutionFormAiImpl(ai, mode, executor, timeout);
    }
}
