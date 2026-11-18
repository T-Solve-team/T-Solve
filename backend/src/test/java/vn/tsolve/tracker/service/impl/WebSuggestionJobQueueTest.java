package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.worker.service.WebJobQueue;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class WebSuggestionJobQueueTest {

    private static final UUID SUGGESTION = UUID.fromString("7f3a2b1c-0000-4000-8000-000000000002");
    private static final SuggestionJobRequest REQUEST = new SuggestionJobRequest(SUGGESTION, "DP-121");

    @Mock
    private WebJobQueue jobs;

    @Test
    void queuesOnlyTheReferenceForTheWorker_AC_011_10_BUG_342() {
        new WebSuggestionJobQueue(jobs).enqueue(REQUEST);

        verify(jobs).enqueue("tracker.suggestion", SUGGESTION);
    }

    @Test
    void logsWithoutThrowingWhenTheJobCannotBeQueued_AC_011_11_BUG_342(CapturedOutput output) {
        given(jobs.enqueue(SuggestionJobHandler.TYPE, SUGGESTION))
                .willThrow(new IllegalStateException("No single Workspace to queue the job for"));

        assertThatCode(() -> new WebSuggestionJobQueue(jobs).enqueue(REQUEST)).doesNotThrowAnyException();

        assertThat(output).contains("DP-121").contains("IllegalStateException");
    }
}
