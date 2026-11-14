package vn.tsolve.platform.service.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import vn.tsolve.common.tenant.TenantContext;

/**
 * Stand-in binding one fixed Workspace for local file storage until per-request Workspace binding exists.
 *
 * <p>Never set {@code tsolve.stub.workspace-id} ({@code TSOLVE_STUB_WORKSPACE_ID}) in production. An empty setting
 * creates no bean; an invalid UUID fails startup. Delete this class when ThreadLocalTenantContext (platform Task 6)
 * is merged.
 */
@Component
@ConditionalOnExpression("!'${tsolve.stub.workspace-id:}'.isBlank()")
public class StubTenantContext implements TenantContext {

    private final UUID workspaceId;

    public StubTenantContext(@Value("${tsolve.stub.workspace-id:}") String workspaceId) {
        try {
            this.workspaceId = UUID.fromString(workspaceId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "tsolve.stub.workspace-id (TSOLVE_STUB_WORKSPACE_ID) must be a valid UUID", e);
        }
    }

    @Override
    public Optional<UUID> currentWorkspaceId() {
        return Optional.of(workspaceId);
    }

    @Override
    public UUID requireWorkspaceId() {
        return workspaceId;
    }

    // ponytail: one fixed Workspace for local development; delete this class when ThreadLocalTenantContext (platform
    // Task 6) is merged
    @Override
    public <T> T runIn(UUID workspaceId, Supplier<T> action) {
        return action.get();
    }

    @Override
    public void runIn(UUID workspaceId, Runnable action) {
        action.run();
    }
}
