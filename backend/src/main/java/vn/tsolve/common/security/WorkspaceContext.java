package vn.tsolve.common.security;

import java.util.function.Supplier;

/**
 * The Workspace (tenant) the current thread acts for. Hibernate reads it through {@code WorkspaceTenantResolver}
 * when a session opens, so set it before the transaction starts (architecture, Multi-tenancy).
 */
public final class WorkspaceContext {

    /** No Workspace set: reads of tenant tables find nothing and inserts fail on the foreign key. */
    public static final long NONE = -1L;

    /** Every Workspace: only for lookups before the Workspace is known and for cross-Workspace jobs. */
    public static final long ROOT = 0L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private WorkspaceContext() {}

    public static long current() {
        Long workspaceId = CURRENT.get();
        return workspaceId == null ? NONE : workspaceId;
    }

    /** Runs {@code action} as {@code workspaceId}, then restores the previous Workspace. */
    public static <T> T callAs(long workspaceId, Supplier<T> action) {
        Long previous = CURRENT.get();
        CURRENT.set(workspaceId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(long workspaceId, Runnable action) {
        callAs(workspaceId, () -> {
            action.run();
            return null;
        });
    }
}
