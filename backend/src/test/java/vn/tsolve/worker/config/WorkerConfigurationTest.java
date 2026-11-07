package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class WorkerConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WorkerConfiguration.class, WorkerMockConfiguration.class);

    @Test
    void registersNeitherWorkerNorMockByDefault_AC_001_12_AC_001_14() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JobRunrRuntime.class);
            assertThat(context).doesNotHaveBean(TenantJobService.class);
            assertThat(context).doesNotHaveBean(WorkerTenantService.class);
        });
    }

    @Test
    void leavesDisabledWorkerPropertiesUnregisteredDuringApplicationScan_BUG_136_AC_001_14() {
        runner.withUserConfiguration(PropertyScan.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WorkerProperties.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = WorkerProperties.class)
    static class PropertyScan {}

    @Test
    void refusesEnabledIntegrationWithoutPlatformAdapter_AC_001_12() {
        runner.withPropertyValues("tsolve.worker.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("WorkerTenantService");
        });
    }

    @Test
    void mockRequiresExplicitProfileAndDeniesUnlistedWorkspaces_AC_001_11() {
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
    void acceptsEmptyMockAllowListsAndDeniesEveryWorkspace_BUG_135_AC_001_11() {
        runner.withPropertyValues("spring.profiles.active=worker-mock").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerTenantService.class).isWorkspaceActive(UUID.randomUUID()))
                    .isFalse();
        });
    }

    @Test
    void rejectsMissingJobsConfigurationInsteadOfFallingBack_AC_001_13() {
        runner.withPropertyValues(
                        "spring.profiles.active=worker-mock",
                        "tsolve.worker.enabled=true",
                        "spring.datasource.url=jdbc:postgresql://business.invalid/business")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("jdbcUrl");
                });
    }
}
