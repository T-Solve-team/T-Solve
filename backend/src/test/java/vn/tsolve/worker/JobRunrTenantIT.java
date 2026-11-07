package vn.tsolve.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.config.WorkerConfiguration;
import vn.tsolve.worker.config.WorkerMockConfiguration;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantJobHandler;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

class JobRunrTenantIT {
    private static final PostgreSQLContainer<?> DB =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("jobs");
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID RETRY = UUID.randomUUID();

    @BeforeAll
    static void startDatabase() throws Exception {
        DB.start();
        try (var connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE business");
        }
    }

    @AfterAll
    static void stopDatabase() {
        DB.stop();
    }

    @Test
    void
            persistsAndExecutesAfterContextRestartWithNativeRetry_AC_002_1_AC_002_3_AC_002_10_AC_002_13_AC_002_14_AC_002_16() {
        Observations observations = new Observations();
        AtomicReference<UUID> firstJob = new AtomicReference<>();
        AtomicReference<UUID> secondJob = new AtomicReference<>();
        AtomicReference<UUID> retryJob = new AtomicReference<>();
        var runner = runner(observations);

        runner.withPropertyValues("spring.profiles.active=worker-mock", "tsolve.worker.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JobRunrRuntime.class).isRunning())
                            .isFalse();
                    assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
                    var business = new JdbcTemplate(context.getBean(DataSource.class));
                    assertThat(business.queryForObject("select current_database()", String.class))
                            .isEqualTo("business");
                    assertThat(business.queryForList("select tablename from pg_tables where schemaname='public'"))
                            .isEmpty();
                    WorkerTenantService tenants = context.getBean(WorkerTenantService.class);
                    TenantJobService queue = context.getBean(TenantJobService.class);
                    tenants.runIn(A, () -> {
                        firstJob.set(queue.enqueue("record-test", FIRST));
                        retryJob.set(queue.enqueue("record-test", RETRY));
                    });
                    tenants.runIn(B, () -> secondJob.set(queue.enqueue("record-test", SECOND)));
                    assertThat(tenants.currentWorkspaceId()).isEmpty();
                    assertThat(observations.seen).isEmpty();
                    String json = jobsJdbc()
                            .queryForObject(
                                    "select jobasjson from jobrunr_jobs where id::text=?",
                                    String.class,
                                    firstJob.get().toString());
                    var job = new JobMapper(new JacksonJsonMapper()).deserializeJob(json);
                    assertThat(job.getJobDetails().getJobParameterValues())
                            .containsExactly(new TenantJobRequest(A, "record-test", FIRST));
                });

        runner.withPropertyValues("spring.profiles.active=worker,worker-mock").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JobRunrRuntime.class).isRunning()).isTrue();
            await().atMost(Duration.ofSeconds(75)).untilAsserted(() -> {
                assertThat(observations.seen).containsExactlyInAnyOrderEntriesOf(Map.of(FIRST, A, SECOND, B, RETRY, A));
                assertThat(observations.retryAttempts).hasValue(2);
                for (UUID jobId : new UUID[] {firstJob.get(), secondJob.get(), retryJob.get()}) {
                    assertThat(jobsJdbc()
                                    .queryForObject(
                                            "select state from jobrunr_jobs where id::text=?",
                                            String.class,
                                            jobId.toString()))
                            .isEqualTo("SUCCEEDED");
                }
            });
            String retryJson = jobsJdbc()
                    .queryForObject(
                            "select jobasjson from jobrunr_jobs where id::text=?",
                            String.class,
                            retryJob.get().toString());
            var retried = new JobMapper(new JacksonJsonMapper()).deserializeJob(retryJson);
            assertThat(retried.getJobDetails().getJobParameterValues())
                    .containsExactly(new TenantJobRequest(A, "record-test", RETRY));
            assertThat(retried.getJobStates())
                    .anyMatch(state -> state.getName().name().equals("FAILED"));
            assertThat(context.getBean(WorkerTenantService.class).currentWorkspaceId())
                    .isEmpty();
        });
    }

    private static ApplicationContextRunner runner(Observations observations) {
        String businessUrl = DB.getJdbcUrl().replace("/jobs", "/business");
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withUserConfiguration(WorkerConfiguration.class, WorkerMockConfiguration.class, Handlers.class)
                .withBean(Observations.class, () -> observations)
                .withPropertyValues(
                        "tsolve.worker.jdbc-url=" + DB.getJdbcUrl(),
                        "tsolve.worker.username=" + DB.getUsername(),
                        "tsolve.worker.password=" + DB.getPassword(),
                        "tsolve.worker.mock.active-workspaces=" + A + "," + B,
                        "spring.datasource.url=" + businessUrl,
                        "spring.datasource.username=" + DB.getUsername(),
                        "spring.datasource.password=" + DB.getPassword());
    }

    private static JdbcTemplate jobsJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
    }

    static class Observations {
        final Map<UUID, UUID> seen = new ConcurrentHashMap<>();
        final AtomicInteger retryAttempts = new AtomicInteger();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Handlers {
        @Bean
        TenantJobHandler recordingHandler(WorkerTenantService tenants, Observations observations) {
            return new TenantJobHandler() {
                @Override
                public String handlerType() {
                    return "record-test";
                }

                @Override
                public void execute(UUID recordId) {
                    UUID workspace = tenants.currentWorkspaceId().orElseThrow();
                    assertThat(workspace).isEqualTo(recordId.equals(SECOND) ? B : A);
                    if (recordId.equals(RETRY) && observations.retryAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("Intentional first attempt failure");
                    }
                    observations.seen.put(recordId, workspace);
                }
            };
        }
    }
}
