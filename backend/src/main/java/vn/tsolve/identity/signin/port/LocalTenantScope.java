package vn.tsolve.identity.signin.port;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Interim in-process {@link TenantScope} until the platform adapters replace it: a per-thread binding, cleared in a
 * {@code finally}. It never falls back to a default Workspace.
 */
public class LocalTenantScope implements TenantScope {

    private final ThreadLocal<UUID> bound = new ThreadLocal<>();

    public LocalTenantScope() {}

    @Override
    public UUID currentWorkspaceId() {
        UUID id = bound.get();
        if (id == null) {
            throw new IllegalStateException("no Workspace is bound to this thread");
        }
        return id;
    }

    @Override
    public <T> T runIn(UUID workspaceId, Supplier<T> work) {
        UUID outer = bound.get();
        if (outer != null && !outer.equals(workspaceId)) {
            throw new IllegalStateException("a Workspace is already bound to this thread");
        }
        bound.set(workspaceId);
        try {
            return work.get();
        } finally {
            if (outer == null) {
                bound.remove();
            }
        }
    }
}
