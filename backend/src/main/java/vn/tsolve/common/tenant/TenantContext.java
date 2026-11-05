package vn.tsolve.common.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface TenantContext {
    Optional<UUID> currentWorkspaceId();

    UUID requireWorkspaceId();

    <T> T runIn(UUID workspaceId, Supplier<T> action);

    default void runIn(UUID workspaceId, Runnable action) {
        runIn(workspaceId, () -> {
            action.run();
            return null;
        });
    }
}
