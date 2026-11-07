package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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
    void returnsBeforeTheJobFinishesAndRunsItOnItsOwnThread_AC_011_1() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        doAnswer(call -> {
                    release.await(5, TimeUnit.SECONDS);
                    finished.countDown();
                    return null;
                })
                .when(jobService)
                .run(REQUEST);
        InProcessSuggestionJobQueue queue = new InProcessSuggestionJobQueue(jobService);
        try {
            queue.enqueue(REQUEST);

            // enqueue came back while the job is still blocked: the 202 does not wait for it.
            assertThat(finished.getCount()).isEqualTo(1);
            verify(jobService, timeout(2000)).run(REQUEST);
            release.countDown();
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
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
