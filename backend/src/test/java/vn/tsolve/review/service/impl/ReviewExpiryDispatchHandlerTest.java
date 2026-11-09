package vn.tsolve.review.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import vn.tsolve.review.dto.ReviewExpiryDispatchRequest;
import vn.tsolve.review.dto.ReviewExpiryJobRequest;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class ReviewExpiryDispatchHandlerTest {
    private final JobRunrRuntime runtime = mock(JobRunrRuntime.class);
    private final JobRequestScheduler scheduler = mock(JobRequestScheduler.class);

    @Test
    void enqueuesOnlyActiveWorkspacesWithAttemptOne_AC_014_2() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var tenants = new MockWorkerTenantService(Set.of(first, second), Set.of(UUID.randomUUID()));
        when(runtime.scheduler()).thenReturn(scheduler);
        new ReviewExpiryDispatchHandler(tenants, runtime).run(new ReviewExpiryDispatchRequest());
        verify(scheduler).enqueue(new ReviewExpiryJobRequest(first, 1));
        verify(scheduler).enqueue(new ReviewExpiryJobRequest(second, 1));
        verifyNoMoreInteractions(scheduler);
    }

    @Test
    void emptyActiveListEnqueuesNothing_AC_014_2() {
        new ReviewExpiryDispatchHandler(new MockWorkerTenantService(Set.of(), Set.of()), runtime)
                .run(new ReviewExpiryDispatchRequest());
        verifyNoInteractions(runtime);
    }

    @Test
    void propagatesCatalogFailure_AC_014_2() {
        var tenants = mock(WorkerTenantService.class);
        var failure = new IllegalStateException("catalog unavailable");
        when(tenants.activeWorkspaceIds()).thenThrow(failure);
        assertThatThrownBy(
                        () -> new ReviewExpiryDispatchHandler(tenants, runtime).run(new ReviewExpiryDispatchRequest()))
                .isSameAs(failure);
        verifyNoInteractions(runtime);
    }
}
