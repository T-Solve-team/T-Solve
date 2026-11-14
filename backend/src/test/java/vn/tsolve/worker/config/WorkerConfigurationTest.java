package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class WorkerConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    WorkerConfiguration.class, WorkerMockConfiguration.class, EmailOutboxConfiguration.class);

    @Test
    void registersNeitherWorkerNorMockByDefault_AC_002_12_AC_002_14() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JobRunrRuntime.class);
            assertThat(context).doesNotHaveBean(TenantJobService.class);
            assertThat(context).doesNotHaveBean(WorkerTenantService.class);
            assertThat(context).doesNotHaveBean(EmailOutboxService.class);
            assertThat(context).doesNotHaveBean(EmailOutboxProperties.class);
            assertThat(context).doesNotHaveBean("workerClock");
            assertThat(context).doesNotHaveBean("jobsLiquibase");
        });
    }

    @Test
    void leavesDisabledWorkerPropertiesUnregisteredDuringApplicationScan_BUG_136_AC_002_14() {
        runner.withUserConfiguration(PropertyScan.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WorkerProperties.class);
            assertThat(context).doesNotHaveBean(EmailOutboxProperties.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = WorkerProperties.class)
    static class PropertyScan {}

    @Test
    void refusesEnabledIntegrationWithoutPlatformAdapter_AC_002_12() {
        runner.withPropertyValues("tsolve.worker.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("WorkerTenantService");
        });
    }

    @Test
    void mockRequiresExplicitProfileAndDeniesUnlistedWorkspaces_AC_002_11() {
        UUID active = UUID.randomUUID();
        UUID suspended = UUID.randomUUID();
        runner.withPropertyValues(
                        "spring.profiles.active=worker-mock",
                        "tsolve.worker.mock.active-workspaces=" + active,
                        "tsolve.worker.mock.suspended-workspaces=" + suspended)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MockWorkerTenantService.class);
                    WorkerTenantService tenant = context.getBean(WorkerTenantService.class);
                    assertThat(tenant.isWorkspaceActive(active)).isTrue();
                    assertThat(tenant.isWorkspaceActive(suspended)).isFalse();
                    assertThat(tenant.isWorkspaceActive(UUID.randomUUID())).isFalse();
                });
    }

    @Test
    void acceptsEmptyMockAllowListsAndDeniesEveryWorkspace_BUG_135_AC_002_11() {
        runner.withPropertyValues("spring.profiles.active=worker-mock").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerTenantService.class).isWorkspaceActive(UUID.randomUUID()))
                    .isFalse();
        });
    }

    @Test
    void rejectsMissingJobsConfigurationInsteadOfFallingBack_AC_002_13() {
        runner.withPropertyValues(
                        "spring.profiles.active=worker-mock",
                        "tsolve.worker.enabled=true",
                        "spring.datasource.url=jdbc:postgresql://business.invalid/business")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("jdbcUrl");
                });
    }

    @Test
    @DisplayName("AC-004.1 AC-004.3 AC-002.13 enabled outbox preserves tenant Liquibase and private Jobs pool")
    void enablesOutboxWithoutReplacingTenantMigration() {
        var jobs = mock(DataSource.class);
        var business = mock(DataSource.class);
        var runtime = mock(JobRunrRuntime.class);
        when(runtime.jobsDataSource()).thenReturn(jobs);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LiquibaseAutoConfiguration.class))
                .withUserConfiguration(EmailOutboxConfiguration.class, SkipMigration.class)
                .withBean(JobRunrRuntime.class, () -> runtime)
                .withBean(WorkerTenantService.class, () -> mock(WorkerTenantService.class))
                .withBean("dataSource", DataSource.class, () -> business)
                .withPropertyValues(
                        "tsolve.worker.enabled=true",
                        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmailOutboxService.class);
                    assertThat(context.getBean("workerClock")).isInstanceOf(Clock.class);
                    assertThat(context.getBeansOfType(DataSource.class)).containsOnlyKeys("dataSource");
                    assertThat(context.getBean("liquibase", SpringLiquibase.class)
                                    .getDataSource())
                            .isSameAs(business);
                    assertThat(context.getBean("jobsLiquibase", SpringLiquibase.class)
                                    .getDataSource())
                            .isSameAs(jobs);
                    var properties = context.getBean(EmailOutboxProperties.class);
                    assertThat(properties.interval()).isEqualTo(java.time.Duration.ofSeconds(30));
                    assertThat(properties.backoff())
                            .containsExactly(java.time.Duration.ofMinutes(5), java.time.Duration.ofMinutes(15));
                    assertThat(properties.lease()).isEqualTo(java.time.Duration.ofMinutes(5));
                    assertThat(properties.batchSize()).isEqualTo(20);
                    assertThat(properties.retention()).isEqualTo(java.time.Duration.ofDays(30));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailOutboxProperties.class)
    static class OutboxPropertiesConfiguration {}

    @ParameterizedTest
    @ValueSource(strings = {"5m", "5m,15m,30m"})
    void rejectsBackoffWithoutExactlyTwoWaits_BUG_217(String backoff) {
        new ApplicationContextRunner()
                .withUserConfiguration(OutboxPropertiesConfiguration.class)
                .withPropertyValues("tsolve.worker.enabled=true", "tsolve.worker.email.backoff=" + backoff)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("backoff");
                });
    }

    @Test
    void rejectsNullOrEmptyBackoff_BUG_217() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (List<Duration> backoff : java.util.Arrays.<List<Duration>>asList(null, List.of())) {
                var properties = new EmailOutboxProperties(
                        Duration.ofSeconds(30), backoff, Duration.ofMinutes(5), 20, Duration.ofDays(30));
                assertThat(validator.validate(properties)).anySatisfy(violation -> assertThat(
                                violation.getPropertyPath().toString())
                        .isEqualTo("backoff"));
            }
        }
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
