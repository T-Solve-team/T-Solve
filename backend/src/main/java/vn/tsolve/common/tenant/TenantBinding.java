package vn.tsolve.common.tenant;

import java.util.UUID;

/**
 * The only per-thread Workspace binding, static so non-bean code (the routing DataSource, the transaction manager) reads
 * the same value as {@link ThreadLocalTenantContext}. A plain {@code ThreadLocal}: new threads start unbound.
 */
final class TenantBinding {

    private static final ThreadLocal<UUID> BOUND = new ThreadLocal<>();

    private TenantBinding() {}

    /** The bound Workspace id, or {@code null}. */
    static UUID current() {
        return BOUND.get();
    }

    static UUID require() {
        UUID id = BOUND.get();
        if (id == null) {
            throw new NoTenantException("no Workspace is bound to this thread");
        }
        return id;
    }

    static void set(UUID workspaceId) {
        BOUND.set(workspaceId);
    }

    static void clear() {
        BOUND.remove();
    }
}
