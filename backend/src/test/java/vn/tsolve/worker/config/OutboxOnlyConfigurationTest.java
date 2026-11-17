package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;

/** BUG-339: the web process queues the review email without the worker runtime. */
class OutboxOnlyConfigurationTest {
    private static final String JOBS = "tsolve.worker.jdbc-url=jdbc:postgresql://jobs.invalid:5432/jobs";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxOnlyConfiguration.class, SkipMigration.class)
            .withBean(TenantContext.class, () -> mock(TenantContext.class));

    @Test
    void registersNothingWithoutTheOutboxFlag_BUG_339() {
        runner.withPropertyValues(JOBS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EmailOutboxService.class);
            assertThat(context).doesNotHaveBean("jobsLiquibase");
        });
    }

    @Test
    void queuesFromTheWebProcessWithoutTheWorkerRuntime_BUG_339() {
        runner.withPropertyValues(
                        "tsolve.worker.outbox-enabled=true",
                        JOBS,
                        "tsolve.worker.username=u",
                        "tsolve.worker.password=p")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailOutboxService.class);
                    assertThat(context).hasBean("jobsLiquibase");
                    assertThat(context).doesNotHaveBean(JobRunrRuntime.class);
                    assertThat(context).doesNotHaveBean(TenantJobService.class);
                    // A WorkerTenantService bean would make WorkspaceJiraServiceAccount treat a request as a job.
                    assertThat(context).doesNotHaveBean(WorkerTenantService.class);
                    assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
                });
    }

    @Test
    void takesTheWorkspaceFromTheRequestTenantContext_BUG_339() {
        var tenantContext = mock(TenantContext.class);
        when(tenantContext.currentWorkspaceId()).thenReturn(Optional.empty());
        new ApplicationContextRunner()
                .withUserConfiguration(OutboxOnlyConfiguration.class, SkipMigration.class)
                .withBean(TenantContext.class, () -> tenantContext)
                .withPropertyValues(
                        "tsolve.worker.outbox-enabled=true",
                        JOBS,
                        "tsolve.worker.username=u",
                        "tsolve.worker.password=p")
                .run(context -> {
                    // No Workspace bound: the outbox refuses before it touches the Jobs database.
                    var outbox = context.getBean(EmailOutboxService.class);
                    var email = new OutboundEmail("a@example.test", "Subject", "Body", null);
                    assertThatThrownBy(() -> outbox.queue(email)).isInstanceOf(InvalidOutboundEmailException.class);
                    verify(tenantContext).currentWorkspaceId();
                });
    }

    @Test
    void refusesMissingJobsDatabaseInsteadOfFallingBack_BUG_339() {
        runner.withPropertyValues("tsolve.worker.outbox-enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("jdbcUrl");
        });
    }

    @Test
    void backsOffWhenTheFullWorkerIsEnabled_BUG_339() {
        runner.withPropertyValues("tsolve.worker.outbox-enabled=true", "tsolve.worker.enabled=true", JOBS)
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
