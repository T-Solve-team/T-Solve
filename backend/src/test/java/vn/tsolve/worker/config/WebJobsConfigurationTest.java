package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.tsolve.common.tenant.NoTenantException;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WebJobQueue;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.impl.CatalogWorkerTenantService;
import vn.tsolve.worker.support.FixedWorkspaceCatalog;

/** BUG-342: the web process queues tenant jobs for the worker process without running JobRunr itself. */
class WebJobsConfigurationTest {
    private static final String JOBS = "tsolve.worker.jdbc-url=jdbc:postgresql://jobs.invalid:5432/jobs";
    private static final String OUTBOX = "tsolve.worker.outbox-enabled=true";
    private static final String WEB_JOBS = "tsolve.worker.web-jobs-enabled=true";
    private static final UUID A = new UUID(0, 1);

    /**
     * The staging backend: no profile, worker disabled; the holder is the only TenantContext, and the Registry gate is
     * on, so a catalog and the worker's catalog service are both in the context, yet no WorkerTenantService bean exists.
     */
    private final ApplicationContextRunner staging = new ApplicationContextRunner()
            .withUserConfiguration(
                    OutboxOnlyConfiguration.class,
                    WebJobsConfiguration.class,
                    CatalogWorkerTenantService.class,
                    OutboxOnlyConfigurationTest.SkipMigration.class)
            .withBean(WorkspaceCatalog.class, () -> new FixedWorkspaceCatalog(Map.of(A, WorkspaceStatus.ACTIVE)))
            .withBean(ThreadLocalTenantContext.class, () -> new ThreadLocalTenantContext(id -> true));

    @Test
    void registersNothingWithoutTheFlag_BUG_342() {
        staging.withPropertyValues(OUTBOX, JOBS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WebJobQueue.class);
        });
    }

    @Test
    void startsWithoutTouchingTheJobsDatabaseOrAnyJobRuntimeBean_AC_011_10_BUG_342() {
        // jobs.invalid cannot be reached: the start must not need it.
        staging.withPropertyValues(OUTBOX, WEB_JOBS, JOBS).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(WebJobQueue.class);
            // Each of these would make Wiki or WorkspaceJiraServiceAccount treat a request as a worker job.
            assertThat(context).doesNotHaveBean(JobRunrRuntime.class);
            assertThat(context).doesNotHaveBean(TenantJobService.class);
            assertThat(context).doesNotHaveBean(WorkerTenantService.class);
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        });
    }

    @Test
    @DisplayName(
            "AC-002.16 enqueue with nothing bound throws NoTenantException before any JobRunr runtime is created, even with one Active Workspace in the catalog")
    void refusesToQueueWithNothingBound_AC_002_16() {
        staging.withPropertyValues(OUTBOX, WEB_JOBS, JOBS).run(context -> {
            var jobs = context.getBean(WebJobQueue.class);
            // Refused before the Jobs database (jobs.invalid, unreachable) is touched.
            assertThatThrownBy(() -> jobs.enqueue("tracker.suggestion", A)).isInstanceOf(NoTenantException.class);
        });
    }

    @Test
    void backsOffWhenTheFullWorkerIsEnabled_BUG_342() {
        staging.withPropertyValues(OUTBOX, WEB_JOBS, "tsolve.worker.enabled=true", JOBS)
                .run(context -> assertThat(context).doesNotHaveBean(WebJobQueue.class));
    }
}
