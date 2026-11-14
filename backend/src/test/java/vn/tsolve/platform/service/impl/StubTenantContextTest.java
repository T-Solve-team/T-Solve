package vn.tsolve.platform.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.tsolve.common.tenant.TenantContext;

class StubTenantContextTest {

    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void returnsConfiguredWorkspaceIdFromBothMethods() {
        StubTenantContext context = new StubTenantContext(WORKSPACE_ID.toString());

        assertThat(context.currentWorkspaceId()).contains(WORKSPACE_ID);
        assertThat(context.requireWorkspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void runInReturnsSupplierResultWithoutChangingWorkspace() {
        StubTenantContext context = new StubTenantContext(WORKSPACE_ID.toString());

        String result = context.runIn(OTHER_WORKSPACE_ID, () -> {
            assertThat(context.requireWorkspaceId()).isEqualTo(WORKSPACE_ID);
            return "stored";
        });

        assertThat(result).isEqualTo("stored");
        assertThat(context.requireWorkspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void runInRunsRunnableWithoutChangingWorkspace() {
        StubTenantContext context = new StubTenantContext(WORKSPACE_ID.toString());
        AtomicBoolean ran = new AtomicBoolean();

        context.runIn(OTHER_WORKSPACE_ID, () -> {
            assertThat(context.requireWorkspaceId()).isEqualTo(WORKSPACE_ID);
            ran.set(true);
        });

        assertThat(ran).isTrue();
        assertThat(context.requireWorkspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void rejectsInvalidUuidWithClearMessage() {
        assertThatThrownBy(() -> new StubTenantContext("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tsolve.stub.workspace-id (TSOLVE_STUB_WORKSPACE_ID) must be a valid UUID");
    }

    @Test
    void createsBeanOnlyWhenWorkspaceIdIsSet() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(StubTenantContext.class);

        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TenantContext.class));
        runner.withPropertyValues("tsolve.stub.workspace-id=")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TenantContext.class));
        runner.withPropertyValues("tsolve.stub.workspace-id= ")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TenantContext.class));
        runner.withPropertyValues("tsolve.stub.workspace-id=" + WORKSPACE_ID).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TenantContext.class);
            assertThat(context.getBean(TenantContext.class).requireWorkspaceId())
                    .isEqualTo(WORKSPACE_ID);
        });
    }
}
