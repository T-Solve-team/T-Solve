package vn.tsolve.common.tenant;

import java.util.Optional;
import java.util.function.Supplier;

public interface TenantContext {
    Optional<Long> currentWorkspaceId();

    Long requireWorkspaceId();

    <T> T runIn(Long workspaceId, Supplier<T> action);

    default void runIn(Long workspaceId, Runnable action) {
        runIn(workspaceId, () -> {
            action.run();
            return null;
        });
    }
}
