package vn.tsolve.identity.signin.port;

import java.util.UUID;
import java.util.function.Supplier;
import vn.tsolve.common.tenant.TenantContext;

/** The identity {@link TenantScope} as a delegate of the one holder: same binding, same Workspace list. */
public class PlatformTenantScope implements TenantScope {

    private final TenantContext holder;

    public PlatformTenantScope(TenantContext holder) {
        this.holder = holder;
    }

    @Override
    public UUID currentWorkspaceId() {
        return holder.requireWorkspaceId();
    }

    @Override
    public <T> T runIn(UUID workspaceId, Supplier<T> work) {
        return holder.runIn(workspaceId, work);
    }
}
