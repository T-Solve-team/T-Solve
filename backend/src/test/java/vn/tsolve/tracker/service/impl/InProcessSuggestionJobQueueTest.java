package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.service.SuggestionJobService;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InProcessSuggestionJobQueueTest {

    private static final SuggestionJobRequest REQUEST =
            new SuggestionJobRequest("ITSUP-1", 4L, "VPN drops every hour", "Reconnect fails.");

    @Mock
    private SuggestionJobService jobService;

    @Test
    void runsTheJobOnItsOwnThreadWithoutWaiting_AC_011_1() throws Exception {
        InProcessSuggestionJobQueue queue = new InProcessSuggestionJobQueue(jobService);
        try {
            queue.enqueue(REQUEST);

            verify(jobService, timeout(2000)).run(REQUEST);
        } finally {
            queue.destroy();
        }
    }

    @Test
    void dropsAndLogsAJobItCannotQueueWithoutThrowing_AC_011_1(CapturedOutput output) {
        InProcessSuggestionJobQueue queue = new InProcessSuggestionJobQueue(jobService, task -> {
            throw new RejectedExecutionException("full");
        });

        assertThatCode(() -> queue.enqueue(REQUEST)).doesNotThrowAnyException();

        verifyNoInteractions(jobService);
        assertThat(output).contains("ITSUP-1").doesNotContain("VPN drops");
    }
}
