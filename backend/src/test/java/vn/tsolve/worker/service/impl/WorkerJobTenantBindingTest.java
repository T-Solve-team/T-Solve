package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import vn.tsolve.common.tenant.NoTenantException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.identity.signin.SignInConfig;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.platform.config.StorageConfig;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.platform.service.impl.ObjectFileStorage;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantJobHandler;

/**
 * A job reads its Workspace from the one holder (Task 6a-1, on the catalog chain since Task 4c). The chain under test:
 * holder (knows the Workspaces the catalog has), the identity {@code PlatformTenantScope},
 * {@code CatalogWorkerTenantService} over the catalog (Active A and B) and {@code TenantExecutionServiceImpl}.
 */
class WorkerJobTenantBindingTest {

    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-4000-8000-00000000000c");
    private static final String FILE_KEY = "7f3a2b1c-0000-4000-8000-000000000001";

    private final WorkspaceCatalog catalog = activeCatalog();
    // The rule of SignInConfig.knownWorkspaces: a Workspace is known when the catalog has its row.
    private final TenantContext holder =
            new ThreadLocalTenantContext(id -> catalog.findById(id).isPresent());
    private final TenantScope scope = new SignInConfig().tenantScope(holder);
    private final CatalogWorkerTenantService worker = new CatalogWorkerTenantService(catalog, holder);
    private final TenantExecutionServiceImpl jobs = new TenantExecutionServiceImpl(worker);

    /** A and B are Active in the catalog; C is not in it. */
    private static WorkspaceCatalog activeCatalog() {
        WorkspaceCatalog catalog = mock(WorkspaceCatalog.class);
        List<WorkspaceSummary> active = new java.util.ArrayList<>();
        for (UUID id : new UUID[] {A, B}) {
            WorkspaceSummary summary = new WorkspaceSummary(
                    id,
                    "ws-" + id,
                    WorkspaceStatus.ACTIVE,
                    "example.test",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
            when(catalog.findById(id)).thenReturn(Optional.of(summary));
            active.add(summary);
        }
        when(catalog.findActive()).thenReturn(active);
        return catalog;
    }

    @AfterEach
    void nothingIsLeftBound() {
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.12 code in a job reads the job's Workspace from TenantContext, TenantScope and WorkerTenantService, and none after the job")
    void aJobSeesItsWorkspaceThroughEveryReader() {
        AtomicReference<Optional<UUID>> fromHolder = new AtomicReference<>();
        AtomicReference<UUID> fromScope = new AtomicReference<>();
        AtomicReference<Optional<UUID>> fromWorker = new AtomicReference<>();

        jobs.runInTenant(A, () -> {
            fromHolder.set(holder.currentWorkspaceId());
            fromScope.set(scope.currentWorkspaceId());
            fromWorker.set(worker.currentWorkspaceId());
        });

        assertThat(fromHolder.get()).contains(A);
        assertThat(fromScope.get()).isEqualTo(A);
        assertThat(fromWorker.get()).contains(A);
        assertThat(holder.currentWorkspaceId()).isEmpty();
        assertThat(worker.currentWorkspaceId()).isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.12 edge 2 a job that throws leaves no Workspace bound, so the next job on the same thread runs in its own Workspace")
    void aFailingJobLeavesNothingBoundForTheNextJob() {
        IllegalStateException failure = new IllegalStateException("job failed");
        assertThatThrownBy(() -> jobs.runInTenant(A, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertThat(holder.currentWorkspaceId()).isEmpty();

        AtomicReference<Optional<UUID>> nextJobSees = new AtomicReference<>();
        jobs.runInTenant(B, () -> nextJobSees.set(holder.currentWorkspaceId()));

        assertThat(nextJobSees.get()).contains(B);
    }

    @Test
    @DisplayName("AC-002.13 edge 4 a job of another Workspace started while one is bound throws and does not run")
    void aJobOfAnotherWorkspaceInsideARequestBindingIsRefused() {
        AtomicInteger ran = new AtomicInteger();
        AtomicReference<Optional<UUID>> afterRefusal = new AtomicReference<>();

        holder.runIn(A, () -> {
            assertThatThrownBy(() -> jobs.runInTenant(B, ran::incrementAndGet)).isInstanceOf(RuntimeException.class);
            afterRefusal.set(holder.currentWorkspaceId());
        });

        assertThat(ran).hasValue(0);
        assertThat(afterRefusal.get()).contains(A);
    }

    @Test
    @DisplayName("AC-002.14 a job for a Workspace the catalog does not know is refused and does not run")
    void aJobForAWorkspaceTheCatalogDoesNotKnowIsRefused() {
        AtomicInteger ran = new AtomicInteger();

        assertThatThrownBy(() -> jobs.runInTenant(C, ran::incrementAndGet))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable or suspended");

        assertThat(ran).hasValue(0);
    }

    @Test
    @DisplayName(
            "AC-002.16 ObjectFileStorage in a job reads under the job's Workspace, and on an unbound thread throws NoTenantException before touching storage")
    void objectStorageReadsUnderTheJobsWorkspaceAndRefusesAnUnboundThread() {
        S3Client s3 = mock(S3Client.class);
        var holderBeans = new StaticListableBeanFactory();
        holderBeans.addBean("holder", holder);
        var properties = new StorageConfig.StorageProperties(
                "http://storage.invalid", "key", "secret", "bucket", "http://{site}.invalid", 60L, 1024L);
        var storage = new ObjectFileStorage(
                s3,
                properties,
                holderBeans.getBeanProvider(TenantContext.class),
                new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class));

        jobs.runInTenant(A, () -> {
            InputStream ignored = storage.read(FILE_KEY);
        });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<GetObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3).getObject(captor.capture());
        GetObjectRequest.Builder builder = GetObjectRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().key()).isEqualTo(A + "/" + FILE_KEY);
        assertThat(builder.build().bucket()).isEqualTo("bucket");

        S3Client untouched = mock(S3Client.class);
        var unbound = new ObjectFileStorage(
                untouched,
                properties,
                holderBeans.getBeanProvider(TenantContext.class),
                new StaticListableBeanFactory().getBeanProvider(WorkspaceCatalog.class));
        assertThatThrownBy(() -> unbound.read(FILE_KEY)).isInstanceOf(NoTenantException.class);
        verifyNoInteractions(untouched);
    }

    @Test
    @DisplayName("AC-002.12 an after-commit enqueue on a bound request queues the job for the request's Workspace")
    void anAfterCommitEnqueueQueuesForTheRequestsWorkspace() {
        JobRequestScheduler scheduler = mock(JobRequestScheduler.class);
        when(scheduler.enqueue(any(JobRequest.class))).thenReturn(new JobId(UUID.randomUUID()));
        TenantJobHandler wikiIndex = new TenantJobHandler() {
            @Override
            public String handlerType() {
                return "wiki-index";
            }

            @Override
            public void execute(UUID recordId) {}
        };
        var queue = new TenantJobServiceImpl(worker, scheduler, new TenantJobRequestHandler(jobs, List.of(wikiIndex)));
        UUID document = UUID.randomUUID();

        // Two Active Workspaces, so there is no single-Workspace guess to fall back on.
        holder.runIn(A, () -> queue.enqueueFromRequest("wiki-index", document));

        ArgumentCaptor<JobRequest> captor = ArgumentCaptor.forClass(JobRequest.class);
        verify(scheduler).enqueue(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(TenantJobRequest.class, request -> {
            assertThat(request.workspaceId()).isEqualTo(A);
            assertThat(request.handlerType()).isEqualTo("wiki-index");
            assertThat(request.recordId()).isEqualTo(document);
        });
    }
}
