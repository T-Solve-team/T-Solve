package vn.tsolve.review.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import vn.tsolve.review.dto.ReviewExpiryDispatchRequest;
import vn.tsolve.review.dto.ReviewExpiryJobRequest;
import vn.tsolve.review.service.ReviewExpiryService;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.service.impl.TenantExecutionServiceImpl;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class ReviewExpiryJobHandlerTest {
    private final UUID workspace = UUID.randomUUID();
    private final UUID suspended = UUID.randomUUID();
    private final MockWorkerTenantService tenants = new MockWorkerTenantService(Set.of(workspace), Set.of(suspended));
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T17:30:00Z"), ZoneOffset.UTC);
    private final ReviewExpiryService expiry = mock(ReviewExpiryService.class);
    private final JobRunrRuntime runtime = mock(JobRunrRuntime.class);
    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
    private final ReviewExpiryJobHandler handler =
            new ReviewExpiryJobHandler(tenants, new TenantExecutionServiceImpl(tenants), expiry, runtime, clock);

    @Test
    void expiresInsideTheWorkspaceUsingTheVietnamDate_AC_014_3() {
        when(expiry.expireOverdue(LocalDate.of(2026, 11, 9))).thenAnswer(call -> {
            assertThat(tenants.currentWorkspaceId()).contains(workspace);
            return Map.of();
        });
        handler.run(new ReviewExpiryJobRequest(workspace, 1));
        verify(expiry).expireOverdue(LocalDate.of(2026, 11, 9));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
        verifyNoInteractions(runtime);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void schedulesTheNextAttemptFifteenMinutesLater_AC_014_6(int attempt) {
        when(runtime.scheduler()).thenReturn(scheduler);
        when(expiry.expireOverdue(LocalDate.of(2026, 11, 9))).thenThrow(new IllegalStateException("private SQL value"));
        handler.run(new ReviewExpiryJobRequest(workspace, attempt));
        verify(scheduler)
                .schedule(clock.instant().plusSeconds(900), new ReviewExpiryJobRequest(workspace, attempt + 1));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void stopsAfterTheThirdAttempt_AC_014_6() {
        when(expiry.expireOverdue(LocalDate.of(2026, 11, 9))).thenThrow(new IllegalStateException("private SQL value"));
        handler.run(new ReviewExpiryJobRequest(workspace, 3));
        verifyNoInteractions(runtime);
    }

    @Test
    void failureLogsContainNoExceptionMessageOrStackTrace_AC_014_6() {
        Logger logger = (Logger) LoggerFactory.getLogger(ReviewExpiryJobHandler.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            when(expiry.expireOverdue(LocalDate.of(2026, 11, 9)))
                    .thenThrow(new IllegalStateException("private SQL ticket content"));
            handler.run(new ReviewExpiryJobRequest(workspace, 3));
            assertThat(events.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getFormattedMessage())
                        .contains(workspace.toString())
                        .doesNotContain("private SQL ticket content");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(events.list.getFirst().getFormattedMessage())
                    .contains("3", IllegalStateException.class.getName());
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void propagatesRetrySchedulingFailure_AC_014_6() {
        when(runtime.scheduler()).thenReturn(scheduler);
        when(expiry.expireOverdue(LocalDate.of(2026, 11, 9))).thenThrow(new IllegalStateException("expiry failed"));
        var failure = new IllegalStateException("scheduler failed");
        when(scheduler.schedule(clock.instant().plusSeconds(900), new ReviewExpiryJobRequest(workspace, 2)))
                .thenThrow(failure);
        assertThatThrownBy(() -> handler.run(new ReviewExpiryJobRequest(workspace, 1)))
                .isSameAs(failure);
    }

    @Test
    void skipsSuspendedAndUnknownWorkspacesWithoutRetry_AC_014_7() {
        handler.run(new ReviewExpiryJobRequest(suspended, 1));
        handler.run(new ReviewExpiryJobRequest(UUID.randomUUID(), 1));
        verifyNoInteractions(expiry, runtime);
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void payloadsContainOnlyWorkspaceAndAttempt_AC_014_8() {
        assertThat(ReviewExpiryJobRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("workspaceId", "attempt");
        assertThat(ReviewExpiryJobRequest.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(UUID.class, int.class);
        assertThat(ReviewExpiryDispatchRequest.class.getRecordComponents()).isEmpty();
    }
}
