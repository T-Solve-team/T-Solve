package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.common.tenant.NoTenantException;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

/**
 * BUG-339: the web process queues the review email without the worker runtime. Task 6a-2: the Workspace is the one
 * the holder has bound, never an id from the environment.
 */
class OutboxOnlyConfigurationTest {
    private static final String JOBS = "tsolve.worker.jdbc-url=jdbc:postgresql://jobs.invalid:5432/jobs";
    private static final String ENABLED = "tsolve.worker.outbox-enabled=true";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final OutboundEmail EMAIL = new OutboundEmail("a@example.test", "Subject", "Body", null);

    private final ThreadLocalTenantContext holder = new ThreadLocalTenantContext(id -> true);
    private final EmailOutboxRepository repository = mock(EmailOutboxRepository.class);

    /** The staging backend: no profile, worker disabled; the holder is the only TenantContext (it is in every process). */
    private final ApplicationContextRunner staging = new ApplicationContextRunner()
            .withUserConfiguration(OutboxOnlyConfiguration.class, SkipMigration.class)
            .withBean(ThreadLocalTenantContext.class, () -> holder);

    @Test
    void registersNothingWithoutTheOutboxFlag_BUG_339() {
        staging.withPropertyValues(JOBS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EmailOutboxService.class);
            assertThat(context).doesNotHaveBean("jobsLiquibase");
        });
    }

    @Test
    void queuesFromTheWebProcessWithoutTheWorkerRuntime_BUG_339() {
        staging.withPropertyValues(ENABLED, JOBS, "tsolve.worker.username=u", "tsolve.worker.password=p")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailOutboxService.class);
                    assertThat(context).hasBean("jobsLiquibase");
                    assertThat(context).doesNotHaveBean(JobRunrRuntime.class);
                    assertThat(context).doesNotHaveBean(TenantJobService.class);
                    // A WorkerTenantService bean would make other modules treat a request as a job.
                    assertThat(context).doesNotHaveBean(WorkerTenantService.class);
                    assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
                });
    }

    @Test
    @DisplayName("AC-002.12 the outbox queues for the Workspace bound in the holder, with no Workspace bean of its own")
    void queuesForTheWorkspaceBoundInTheHolder_AC_002_12() {
        staging.withPropertyValues(ENABLED, JOBS, "tsolve.worker.password=p")
                .withAllowBeanDefinitionOverriding(true)
                .withBean("emailOutboxRepository", EmailOutboxRepository.class, () -> repository)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailOutboxService.class);
                    assertThat(context).doesNotHaveBean("outboxWorkspace");

                    holder.runIn(
                            A, () -> context.getBean(EmailOutboxService.class).queue(EMAIL));

                    verify(repository).insert(any(), eq(A), any(), any());
                });
    }

    @Test
    @DisplayName("AC-002.16 the outbox refuses with nothing bound, even when the mock list names exactly one Workspace")
    void refusesWithNothingBoundEvenWithOneMockWorkspace_AC_002_16() {
        staging.withPropertyValues(
                        ENABLED,
                        JOBS,
                        "tsolve.worker.password=p",
                        "tsolve.worker.mock.active-workspaces=" + A,
                        "TSOLVE_MOCK_ACTIVE_WORKSPACES=" + A)
                .withAllowBeanDefinitionOverriding(true)
                .withBean("emailOutboxRepository", EmailOutboxRepository.class, () -> repository)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var outbox = context.getBean(EmailOutboxService.class);

                    assertThatThrownBy(() -> outbox.queue(EMAIL)).isInstanceOf(NoTenantException.class);

                    verifyNoInteractions(repository);
                });
    }

    @Test
    @DisplayName("edge 4 the outbox queues for the bound Workspace, never for the one in the mock list")
    void neverSwitchesToTheMockListWorkspace_edge_4() {
        staging.withPropertyValues(
                        ENABLED, JOBS, "tsolve.worker.password=p", "tsolve.worker.mock.active-workspaces=" + B)
                .withAllowBeanDefinitionOverriding(true)
                .withBean("emailOutboxRepository", EmailOutboxRepository.class, () -> repository)
                .run(context -> {
                    assertThat(context).doesNotHaveBean("outboxWorkspace");

                    holder.runIn(
                            A, () -> context.getBean(EmailOutboxService.class).queue(EMAIL));

                    verify(repository).insert(any(), eq(A), any(), any());
                });
    }

    @Test
    void refusesMissingJobsDatabaseInsteadOfFallingBack_BUG_339() {
        staging.withPropertyValues(ENABLED).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("jdbcUrl");
        });
    }

    @Test
    void backsOffWhenTheFullWorkerIsEnabled_BUG_339() {
        staging.withPropertyValues(ENABLED, "tsolve.worker.enabled=true", JOBS)
                .run(context -> assertThat(context).doesNotHaveBean(EmailOutboxService.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class SkipMigration {
        @Bean
        static BeanPostProcessor skipMigration() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (bean instanceof SpringLiquibase liquibase) {
                        var migration = spy(liquibase);
                        try {
                            doNothing().when(migration).afterPropertiesSet();
                        } catch (liquibase.exception.LiquibaseException failure) {
                            throw new IllegalStateException(failure);
                        }
                        return migration;
                    }
                    return bean;
                }
            };
        }
    }
}
