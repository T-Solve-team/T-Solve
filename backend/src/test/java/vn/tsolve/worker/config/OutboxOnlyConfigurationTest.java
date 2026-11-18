package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.OutboxWorkspace;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

/** BUG-339: the web process queues the review email without the worker runtime. BUG-340: and without a TenantContext. */
class OutboxOnlyConfigurationTest {
    private static final String JOBS = "tsolve.worker.jdbc-url=jdbc:postgresql://jobs.invalid:5432/jobs";
    private static final String ENABLED = "tsolve.worker.outbox-enabled=true";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    /** The staging backend: no profile, worker disabled, no TenantContext bean (only the stub sets one, and not there). */
    private final ApplicationContextRunner staging =
            new ApplicationContextRunner().withUserConfiguration(OutboxOnlyConfiguration.class, SkipMigration.class);

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
                    // A WorkerTenantService or TenantContext bean would make other modules treat a request as a job.
                    assertThat(context).doesNotHaveBean(WorkerTenantService.class);
                    assertThat(context).doesNotHaveBean(TenantContext.class);
                    assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
                });
    }

    @Test
    void startsWithoutAnyTenantContextBean_BUG_340() {
        staging.withPropertyValues(
                        ENABLED, JOBS, "tsolve.worker.mock.active-workspaces=" + A, "tsolve.worker.password=p")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailOutboxService.class);
                    assertThat(context.getBean(OutboxWorkspace.class).current()).contains(A);
                });
    }

    @Test
    void startsWithoutAnyWorkspaceConfiguredAndRefusesToQueue_BUG_340() {
        staging.withPropertyValues(ENABLED, JOBS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OutboxWorkspace.class).current()).isEmpty();
            var outbox = context.getBean(EmailOutboxService.class);
            var email = new OutboundEmail("a@example.test", "Subject", "Body", null);
            // No Workspace to bind: the outbox refuses before it touches the Jobs database.
            assertThatThrownBy(() -> outbox.queue(email)).isInstanceOf(InvalidOutboundEmailException.class);
        });
    }

    @Test
    void readsTheStagingEnvVariableWithoutTheWorkerMockProfile_BUG_341() {
        // Staging sets only TSOLVE_MOCK_ACTIVE_WORKSPACES; its property mapping lives in application-worker-mock.yml,
        // which the backend (no profile) never loads.
        staging.withPropertyValues(ENABLED, JOBS, "TSOLVE_MOCK_ACTIVE_WORKSPACES=" + A)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OutboxWorkspace.class).current()).contains(A);
                });
    }

    @Test
    void neverGuessesBetweenSeveralMockWorkspaces_BUG_340() {
        staging.withPropertyValues(ENABLED, JOBS, "tsolve.worker.mock.active-workspaces=" + A + ", " + B)
                .run(context -> assertThat(
                                context.getBean(OutboxWorkspace.class).current())
                        .isEmpty());
    }

    @Test
    void prefersTheRequestTenantContextWhenOneExists_BUG_340() {
        var tenantContext = mock(TenantContext.class);
        when(tenantContext.currentWorkspaceId()).thenReturn(Optional.of(B));
        staging.withBean(TenantContext.class, () -> tenantContext)
                .withPropertyValues(ENABLED, JOBS, "tsolve.worker.mock.active-workspaces=" + A)
                .run(context -> assertThat(
                                context.getBean(OutboxWorkspace.class).current())
                        .contains(B));
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
