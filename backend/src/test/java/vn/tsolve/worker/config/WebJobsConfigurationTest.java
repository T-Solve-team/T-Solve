package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WebJobQueue;
import vn.tsolve.worker.service.WorkerTenantService;

/** BUG-342: the web process queues tenant jobs for the worker process without running JobRunr itself. */
class WebJobsConfigurationTest {
    private static final String JOBS = "tsolve.worker.jdbc-url=jdbc:postgresql://jobs.invalid:5432/jobs";
    private static final String OUTBOX = "tsolve.worker.outbox-enabled=true";
    private static final String WEB_JOBS = "tsolve.worker.web-jobs-enabled=true";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    /** The staging backend: no profile, worker disabled, no TenantContext bean. */
    private final ApplicationContextRunner staging = new ApplicationContextRunner()
            .withUserConfiguration(
                    OutboxOnlyConfiguration.class,
                    WebJobsConfiguration.class,
                    OutboxOnlyConfigurationTest.SkipMigration.class);

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
        staging.withPropertyValues(OUTBOX, WEB_JOBS, JOBS, "TSOLVE_MOCK_ACTIVE_WORKSPACES=" + A)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WebJobQueue.class);
                    // Each of these would make Wiki or WorkspaceJiraServiceAccount treat a request as a worker job.
                    assertThat(context).doesNotHaveBean(JobRunrRuntime.class);
                    assertThat(context).doesNotHaveBean(TenantJobService.class);
                    assertThat(context).doesNotHaveBean(WorkerTenantService.class);
                    assertThat(context).doesNotHaveBean(TenantContext.class);
                    assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
                });
    }

    @Test
    void refusesToQueueWithoutASingleWorkspace_AC_011_11_BUG_342() {
        staging.withPropertyValues(OUTBOX, WEB_JOBS, JOBS, "TSOLVE_MOCK_ACTIVE_WORKSPACES=" + A + "," + B)
                .run(context -> {
                    var jobs = context.getBean(WebJobQueue.class);
                    // Refused before the Jobs database is touched.
                    assertThatThrownBy(() -> jobs.enqueue("tracker.suggestion", A))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Workspace");
                });
    }

    @Test
    void failsTheStartWhenTheOutboxIsOff_BUG_342() {
        staging.withPropertyValues(WEB_JOBS, JOBS).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("tsolve.worker.outbox-enabled");
        });
    }

    @Test
    void backsOffWhenTheFullWorkerIsEnabled_BUG_342() {
        staging.withPropertyValues(OUTBOX, WEB_JOBS, "tsolve.worker.enabled=true", JOBS)
                .run(context -> assertThat(context).doesNotHaveBean(WebJobQueue.class));
    }
}
