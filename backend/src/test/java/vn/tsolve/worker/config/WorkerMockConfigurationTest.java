package vn.tsolve.worker.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.service.impl.StubTenantContext;
import vn.tsolve.worker.service.mock.MockWorkerTenantService;

class WorkerMockConfigurationTest {
    private static final UUID ACME = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-0000000000a2");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("worker-mock"))
            .withUserConfiguration(WorkerMockConfiguration.class)
            .withPropertyValues("tsolve.worker.mock.active-workspaces=" + ACME + "," + OTHER);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsTheRequestWorkspaceFromTenantContextWhenPresent_BUG_324() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(TenantContext.class);
            assertThat(context.getBean(MockWorkerTenantService.class).enqueueWorkspaceId())
                    .isEmpty();
        });
        runner.withUserConfiguration(StubTenantContext.class)
                .withPropertyValues("tsolve.stub.workspace-id=" + ACME)
                .run(context -> assertThat(
                                context.getBean(MockWorkerTenantService.class).enqueueWorkspaceId())
                        .contains(ACME));
    }
}
