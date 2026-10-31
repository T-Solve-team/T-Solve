package vn.tsolve.identity.signin.port;

import java.util.UUID;
import java.util.function.Supplier;

public interface TenantScope {

    UUID currentWorkspaceId();

    <T> T runIn(UUID workspaceId, Supplier<T> work);
}
