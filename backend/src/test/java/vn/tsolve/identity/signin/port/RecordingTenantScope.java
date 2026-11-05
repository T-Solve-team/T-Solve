package vn.tsolve.identity.signin.port;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * A test {@link TenantScope}: a per-thread binding that refuses to answer when no Workspace is bound (as the real
 * scope must) and records every Workspace {@code runIn} was opened for.
 */
public final class RecordingTenantScope implements TenantScope {

    private final ThreadLocal<UUID> bound = new ThreadLocal<>();
    private final List<UUID> opened = new CopyOnWriteArrayList<>();

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
        opened.add(workspaceId);
        UUID previous = bound.get();
        bound.set(workspaceId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                bound.remove();
            } else {
                bound.set(previous);
            }
        }
    }

    /** Every Workspace id {@link #runIn} was called with since the last reset. */
    public List<UUID> boundIds() {
        return List.copyOf(opened);
    }

    public void reset() {
        opened.clear();
        bound.remove();
    }
}
