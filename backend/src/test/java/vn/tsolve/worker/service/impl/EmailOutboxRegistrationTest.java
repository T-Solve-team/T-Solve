package vn.tsolve.worker.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.worker.config.EmailOutboxProperties;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.dto.EmailOutboxDispatchRequest;

class EmailOutboxRegistrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("AC-004.4 registers the recurring job at the configured interval only with processing enabled")
    void registersOnlyOnWorker(boolean processing) {
        var runtime = mock(JobRunrRuntime.class);
        var scheduler = mock(JobRequestScheduler.class);
        when(runtime.isAutoStartup()).thenReturn(processing);
        when(runtime.scheduler()).thenReturn(scheduler);
        var properties = new EmailOutboxProperties(
                Duration.ofSeconds(45),
                List.of(Duration.ofMinutes(5), Duration.ofMinutes(15)),
                Duration.ofMinutes(5),
                20,
                Duration.ofDays(30));
        new EmailOutboxRegistration(runtime, properties).register();
        if (processing)
            verify(scheduler)
                    .scheduleRecurrently(
                            eq("worker-email-outbox"),
                            eq(Duration.ofSeconds(45)),
                            any(EmailOutboxDispatchRequest.class));
        else verify(runtime, never()).scheduler();
    }
}
