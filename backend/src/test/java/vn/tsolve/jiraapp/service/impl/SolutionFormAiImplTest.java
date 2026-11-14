package vn.tsolve.jiraapp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.tsolve.ai.dto.IssueContent;
import vn.tsolve.ai.dto.SolutionDraft;
import vn.tsolve.ai.exception.AiRewriteUnavailableException;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.ai.service.SolutionAiService;
import vn.tsolve.jiraapp.dto.SolutionFormDraft;
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
    void opensWithTheAiDraftOfTheIssue_AC_002_1() {
        when(ai.draft(new IssueContent("VPN drops", "After sleep", List.of())))
                .thenReturn(new SolutionDraft(AI_SECTIONS, true));

        SolutionFormDraft draft = service(Mode.AI).draft("VPN drops", "After sleep");

        assertThat(draft)
                .isEqualTo(new SolutionFormDraft(
                        new SolutionSections("Drops hourly", "Old client", "Update to 5.2", "", "Laptops"), true));
    }

    @Test
    void fillsSymptomsFromTheIssueWhenTheAiFallsBack_AC_002_2() {
        when(ai.draft(any())).thenReturn(new SolutionDraft(AI_SECTIONS, false));

        SolutionFormDraft draft = service(Mode.AI).draft(" VPN drops ", "After sleep\n");

        assertThat(draft.aiDraft()).isFalse();
        assertThat(draft.sections()).isEqualTo(new SolutionSections("VPN drops\n\nAfter sleep", "", "", "", ""));
    }

    @Test
    void fillsSymptomsFromTheIssueWhenTheAiFails_AC_002_2() {
        when(ai.draft(any())).thenThrow(new AiUnavailableException());

        SolutionFormDraft draft = service(Mode.AI).draft("VPN drops", "");

        assertThat(draft).isEqualTo(new SolutionFormDraft(new SolutionSections("VPN drops", "", "", "", ""), false));
    }

    @Test
    void fillsSymptomsFromTheIssueWhenTheAiMissesTheDeadline_AC_002_2() {
        CountDownLatch release = new CountDownLatch(1);
        when(ai.draft(any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return new SolutionDraft(AI_SECTIONS, true);
        });

        long start = System.nanoTime();
        SolutionFormDraft draft = service(Mode.AI, Duration.ofMillis(100)).draft("VPN drops", "After sleep");

        assertThat(draft.aiDraft()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        release.countDown();
    }

    @Test
    void cutsAnIssueLongerThanTheSolutionLimit_AC_002_2() {
        SolutionSections sections = SolutionFormAiImpl.fromIssue("S", "😀".repeat(12_000));

        assertThat(sections.length()).isEqualTo(SolutionFormAiImpl.MAX_SOLUTION_LENGTH);
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

        assertThat(stub.draft("VPN drops", "").aiDraft()).isTrue();
        assertThat(stub.rewrite(new SolutionSections("drops", "old", "update", "", "laptops"))
                        .symptoms())
                .isEqualTo("drops.");
        verifyNoInteractions(ai);
    }

    @Test
    void behavesAsAnUnavailableAiWhenStubbedUnavailable_AC_002_2_AC_007_4() {
        SolutionFormAiImpl stub = service(Mode.UNAVAILABLE);

        assertThat(stub.draft("VPN drops", "").aiDraft()).isFalse();
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
