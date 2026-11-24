package vn.tsolve.common.tenant;

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

    @Override
    public <T> T runIn(UUID workspaceId, Supplier<T> action) {
        if (workspaceId == null || !known.exists(workspaceId)) {
            throw new IllegalArgumentException("the Workspace is not known");
        }
        UUID outer = BOUND.get();
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
