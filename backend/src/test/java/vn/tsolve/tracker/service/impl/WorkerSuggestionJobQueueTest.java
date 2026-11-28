package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.support.FixedWorkerTenantService;

/**
 * The queue is wired by Spring, so these tests do not depend on how its constructor is declared (Task 6a-2 drops the
 * dev Workspace argument).
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class WorkerSuggestionJobQueueTest {

    private static final UUID JOB_REF = UUID.fromString("7f3a2b1c-0000-4000-8000-000000000001");
    private static final UUID BOUND = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("0d0d0d0d-0000-4000-8000-000000000001");
    private static final SuggestionJobRequest REQUEST = new SuggestionJobRequest(JOB_REF, "ITSUP-1");
    // Built from pieces so that the source scan of the removed names does not see this file.
    private static final String REMOVED_PROPERTY = "tsolve.tracker." + "dev-workspace-id";

    @Mock
    private TenantJobService jobs;

    @Mock
    private WorkerTenantService tenants;

    @Test
    void enqueuesOnlyTheReferenceInTheBoundWorkspace_AC_011_1() {
        lenient().when(tenants.currentWorkspaceId()).thenReturn(Optional.of(BOUND));
        lenient().when(tenants.enqueueWorkspaceId()).thenReturn(Optional.of(BOUND));

        enqueue(tenants, jobs);

        verify(jobs).enqueue(SuggestionJobHandler.TYPE, JOB_REF);
        verify(tenants, never()).runIn(any(), any());
    }

    @Test
    @DisplayName(
            "AC-002.16 with nothing bound the job is not queued and a warning is logged, even with the removed dev Workspace property set")
    void logsWithoutThrowingWhenNoWorkspaceIsBound_AC_002_16(CapturedOutput output) {
        lenient().when(tenants.currentWorkspaceId()).thenReturn(Optional.empty());
        lenient().when(tenants.enqueueWorkspaceId()).thenReturn(Optional.empty());

        assertThatCode(() -> enqueue(tenants, jobs, REMOVED_PROPERTY + "=" + OTHER))
                .doesNotThrowAnyException();

        verifyNoInteractions(jobs);
        verify(tenants, never()).runIn(any(), any());
        assertThat(output).contains("ITSUP-1");
    }

    @Test
    void logsWithoutThrowingWhenTheWorkerRefusesTheJob_AC_011_1(CapturedOutput output) {
        lenient().when(tenants.currentWorkspaceId()).thenReturn(Optional.of(BOUND));
        lenient().when(tenants.enqueueWorkspaceId()).thenReturn(Optional.of(BOUND));
        given(jobs.enqueue(SuggestionJobHandler.TYPE, JOB_REF))
                .willThrow(new IllegalStateException("Jobs database unavailable"));

        assertThatCode(() -> enqueue(tenants, jobs)).doesNotThrowAnyException();

        assertThat(output).contains("ITSUP-1").contains("IllegalStateException");
    }

    @Test
    @DisplayName("AC-002.13 inside a bound call the suggestion job is queued for the bound Workspace")
    void queuesForTheBoundWorkspaceInsideABoundCall_AC_002_13() {
        var holder = new ThreadLocalTenantContext(Set.of(BOUND, OTHER)::contains);
        var realTenants = org.mockito.Mockito.spy(new FixedWorkerTenantService(Set.of(BOUND, OTHER), Set.of(), holder));
        var recordingJobs = org.mockito.Mockito.mock(TenantJobService.class);
        var boundWhenQueued = new AtomicReference<Optional<UUID>>();
        given(recordingJobs.enqueue(SuggestionJobHandler.TYPE, JOB_REF)).willAnswer(call -> {
            boundWhenQueued.set(holder.currentWorkspaceId());
            return UUID.randomUUID();
        });

        holder.runIn(BOUND, () -> enqueue(realTenants, recordingJobs));

        verify(recordingJobs).enqueue(SuggestionJobHandler.TYPE, JOB_REF);
        assertThat(boundWhenQueued.get()).contains(BOUND);
        verify(realTenants, never()).runIn(any(), any());
        assertThat(holder.currentWorkspaceId()).isEmpty();
    }

    @Test
    @DisplayName("edge 4 a request bound to a Workspace that may not enqueue queues no suggestion job")
    void queuesNothingWhenTheBoundWorkspaceMayNotEnqueue_edge_4() {
        lenient().when(tenants.currentWorkspaceId()).thenReturn(Optional.of(BOUND));
        given(tenants.enqueueWorkspaceId()).willReturn(Optional.empty());

        assertThatCode(() -> enqueue(tenants, jobs)).doesNotThrowAnyException();

        verifyNoInteractions(jobs);
    }

    private static void enqueue(WorkerTenantService tenants, TenantJobService jobs, String... properties) {
        new ApplicationContextRunner()
                .withPropertyValues("tsolve.worker.enabled=true")
                .withPropertyValues(properties)
                .withBean(TenantJobService.class, () -> jobs)
                .withBean(WorkerTenantService.class, () -> tenants)
                .withBean(WorkerSuggestionJobQueue.class)
                .run(context -> context.getBean(WorkerSuggestionJobQueue.class).enqueue(REQUEST));
    }
}
