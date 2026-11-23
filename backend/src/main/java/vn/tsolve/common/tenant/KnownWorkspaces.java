package vn.tsolve.common.tenant;

import java.util.UUID;

/** The existence check behind {@link TenantContext#runIn}: an id that is not known is never bound (AC-002.14). */
@FunctionalInterface
public interface KnownWorkspaces {

    boolean exists(UUID workspaceId);
}
