package vn.tsolve.common.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The only per-thread Workspace binding. The {@link ThreadLocal} is static, so every instance (and every delegate)
 * sees the same binding. It is a plain {@code ThreadLocal}: threads started inside a binding start unbound.
 */
@Component
@Primary
public class ThreadLocalTenantContext implements TenantContext {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadLocalTenantContext.class);
    private static final ThreadLocal<UUID> BOUND = new ThreadLocal<>();
    private static final String PLATFORM_TENANT_SCOPE = "vn.tsolve.identity.signin.port.PlatformTenantScope";

    private final KnownWorkspaces known;

    public ThreadLocalTenantContext(KnownWorkspaces known) {
        this.known = known;
    }

    @Override
    public Optional<UUID> currentWorkspaceId() {
        return Optional.ofNullable(BOUND.get());
    }

    @Override
    public UUID requireWorkspaceId() {
        UUID id = BOUND.get();
        if (id == null) {
            logCaller();
            throw new NoTenantException("no Workspace is bound to this thread");
        }
        return id;
    }

    /** A binding that lasts until it is closed, on the thread that made it. */
    public interface RequestBinding extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Binds for one web request: no transaction and no existence check, so only the web Tenant Resolver, which has just
     * read the catalog, may call it.
     */
    public RequestBinding bindRequest(UUID workspaceId) {
        Objects.requireNonNull(workspaceId);
        if (BOUND.get() != null) {
            throw new IllegalStateException("a Workspace is already bound to this thread");
        }
        BOUND.set(workspaceId);
        return BOUND::remove;
    }

    @Override
    public <T> T runIn(UUID workspaceId, Supplier<T> action) {
        UUID outer = BOUND.get();
        // Whoever bound this id has already checked that it exists.
        if (workspaceId == null || (!workspaceId.equals(outer) && !known.exists(workspaceId))) {
            throw new IllegalArgumentException("the Workspace is not known");
        }
        if (outer != null && !outer.equals(workspaceId)) {
            throw new IllegalStateException("another Workspace is already bound to this thread");
        }
        BOUND.set(workspaceId);
        try {
            return action.get();
        } finally {
            if (outer == null) {
                BOUND.remove();
            } else {
                BOUND.set(outer);
            }
        }
    }

    @Override
    public void runIn(UUID workspaceId, Runnable action) {
        runIn(workspaceId, () -> {
            action.run();
            return null;
        });
    }

    /** Logs the first frame outside this class and {@code PlatformTenantScope}; never the Workspace id. */
    private static void logCaller() {
        String own = ThreadLocalTenantContext.class.getName();
        StackWalker.getInstance()
                .walk(frames -> frames.filter(f -> !f.getClassName().equals(own)
                                && !f.getClassName().equals(PLATFORM_TENANT_SCOPE))
                        .findFirst())
                .ifPresent(f ->
                        LOG.warn("No Workspace is bound; {}.{} asked for it", f.getClassName(), f.getMethodName()));
    }
}
