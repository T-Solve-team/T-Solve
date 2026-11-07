package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class ReviewNotificationEnqueueBugTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID RECORD_ID = new UUID(0, 3);

    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
    private final TenantJobRequestHandler handler = mock(TenantJobRequestHandler.class);

    @Test
    void enqueuesOnTheSoleActiveWorkspaceWhenNoneIsBound_BUG_153() {
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of());
        var jobs = new TenantJobServiceImpl(tenants, scheduler, handler);
        when(scheduler.enqueue(any(TenantJobRequest.class))).thenReturn(new JobId(UUID.randomUUID()));

        enqueuer(jobs, tenants).onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID)));

        ArgumentCaptor<TenantJobRequest> request = ArgumentCaptor.forClass(TenantJobRequest.class);
        verify(scheduler).enqueue(request.capture());
        assertThat(request.getValue().workspaceId()).isEqualTo(A);
    }

    @Test
    void refusesWhenSeveralWorkspacesAreActiveAndNoneIsBound_BUG_153() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of());
        var jobs = new TenantJobServiceImpl(tenants, scheduler, handler);

        enqueuer(jobs, tenants).onDecided(new ReviewFeedbackDecidedEvent(List.of(RECORD_ID)));

        verifyNoInteractions(scheduler);
    }

    @Test
    void directEnqueueStillRejectsAnUnboundThread_BUG_153_AC_002_2() {
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of());
        var jobs = new TenantJobServiceImpl(tenants, scheduler, handler);

        assertThatThrownBy(() -> jobs.enqueue(ReviewNotificationJobHandler.TYPE, RECORD_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Enqueue requires a bound workspace");

        verifyNoInteractions(scheduler);
    }

    @Test
    void prefersTheBoundWorkspace_BUG_153() {
        var tenants = new MockWorkerTenantService(Set.of(A, B), Set.of());
        var jobs = new TenantJobServiceImpl(tenants, scheduler, handler);
        when(scheduler.enqueue(any(TenantJobRequest.class))).thenReturn(new JobId(UUID.randomUUID()));

        tenants.runIn(B, () -> jobs.enqueue(ReviewNotificationJobHandler.TYPE, RECORD_ID));

        ArgumentCaptor<TenantJobRequest> request = ArgumentCaptor.forClass(TenantJobRequest.class);
        verify(scheduler).enqueue(request.capture());
        assertThat(request.getValue().workspaceId()).isEqualTo(B);
    }

    @Test
    void keepsCurrentWorkspaceEmptyOutsideAJob_BUG_153() {
        var tenants = new MockWorkerTenantService(Set.of(A), Set.of());

        assertThat(tenants.currentWorkspaceId()).isEmpty();
        tenants.runIn(A, () -> assertThat(tenants.currentWorkspaceId()).contains(A));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void stopsAtTheFirstFailedEnqueue_BUG_153() {
        TenantJobService jobs = mock(TenantJobService.class);
        when(jobs.enqueue(anyString(), any(UUID.class))).thenThrow(new IllegalStateException("Jobs DB unavailable"));
        var event = new ReviewFeedbackDecidedEvent(List.of(RECORD_ID, new UUID(0, 4), new UUID(0, 5)));

        enqueuer(jobs, null).onDecided(event);

        verify(jobs, times(1)).enqueue(eq(ReviewNotificationJobHandler.TYPE), any(UUID.class));
    }

    @SuppressWarnings("unchecked")
    private ReviewNotificationEnqueuer enqueuer(TenantJobService jobs, WorkerTenantService tenants) {
        ObjectProvider<TenantJobService> jobProvider = mock(ObjectProvider.class);
        ObjectProvider<WorkerTenantService> tenantProvider = mock(ObjectProvider.class);
        when(jobProvider.getIfAvailable()).thenReturn(jobs);
        when(tenantProvider.getIfAvailable()).thenReturn(tenants);
        return new ReviewNotificationEnqueuer(jobProvider, tenantProvider);
    }
}
