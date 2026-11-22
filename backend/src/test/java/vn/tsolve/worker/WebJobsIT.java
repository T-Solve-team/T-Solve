package vn.tsolve.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.tsolve.worker.config.OutboxOnlyConfiguration;
import vn.tsolve.worker.config.WebJobsConfiguration;
import vn.tsolve.worker.config.WorkerConfiguration;
import vn.tsolve.worker.config.WorkerMockConfiguration;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantJobHandler;
import vn.tsolve.worker.service.WebJobQueue;
import vn.tsolve.worker.service.WorkerTenantService;

/** BUG-342: a job the web process queues is run by the worker process inside the job's Workspace. */
class WebJobsIT {
    private static final PostgreSQLContainer<?> DB = WorkerJobsDatabase.instance();
    private static final UUID A = UUID.randomUUID();
    private static final UUID RECORD = UUID.randomUUID();
    private static final String TYPE = "web-jobs-test";
    private static final Map<UUID, UUID> SEEN = new ConcurrentHashMap<>();

    @Test
    void theWorkerRunsTheWebJobInItsWorkspace_AC_011_10_BUG_342() {
        UUID[] jobId = new UUID[1];

        // The staging backend: no profile, worker off, only the outbox and web jobs.
        new ApplicationContextRunner()
                .withUserConfiguration(OutboxOnlyConfiguration.class, WebJobsConfiguration.class)
                .withPropertyValues(jobsDatabase())
                .withPropertyValues(
                        "tsolve.worker.outbox-enabled=true",
                        "tsolve.worker.web-jobs-enabled=true",
                        "TSOLVE_MOCK_ACTIVE_WORKSPACES=" + A)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    jobId[0] = context.getBean(WebJobQueue.class).enqueue(TYPE, RECORD);
                });
        String json = jobsJdbc()
                .queryForObject(
                        "select jobasjson from jobrunr_jobs where id::text=?", String.class, jobId[0].toString());
        assertThat(new JobMapper(new JacksonJsonMapper())
                        .deserializeJob(json)
                        .getJobDetails()
                        .getJobParameterValues())
                .containsExactly(new TenantJobRequest(A, TYPE, RECORD));

        // The staging worker container.
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(WorkerConfiguration.class, WorkerMockConfiguration.class, Handler.class)
                .withPropertyValues(jobsDatabase())
                .withPropertyValues(
                        "spring.profiles.active=worker,worker-mock", "tsolve.worker.mock.active-workspaces=" + A)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    await().atMost(Duration.ofSeconds(75))
                            .untilAsserted(() -> assertThat(SEEN).containsExactly(Map.entry(RECORD, A)));
                });
    }

    private static String[] jobsDatabase() {
        return new String[] {
            "tsolve.worker.jdbc-url=" + DB.getJdbcUrl(),
            "tsolve.worker.username=" + DB.getUsername(),
            "tsolve.worker.password=" + DB.getPassword()
        };
    }

    private static JdbcTemplate jobsJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Handler {
        @Bean
        TenantJobHandler webJobsTestHandler(WorkerTenantService tenants) {
            return new TenantJobHandler() {
                @Override
                public String handlerType() {
                    return TYPE;
                }

                @Override
                public void execute(UUID recordId) {
                    SEEN.put(recordId, tenants.currentWorkspaceId().orElseThrow());
                }
            };
        }
    }
}
