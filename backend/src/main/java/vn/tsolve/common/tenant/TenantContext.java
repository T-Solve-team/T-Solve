package vn.tsolve.common.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface TenantContext {
    Optional<UUID> currentWorkspaceId();

    UUID requireWorkspaceId(); // throws when none is bound (AC-002.16)

    <T> T runIn(UUID workspaceId, Supplier<T> action); // AC-002.12 – AC-002.15

    void runIn(UUID workspaceId, Runnable action);
}
