package vn.tsolve.identity.session;

import java.io.Serializable;
import java.security.Principal;

/** Server-issued identity contract; request headers and query parameters never create this principal. */
public record WorkspaceAccountPrincipal(long workspaceId, long accountId) implements Principal, Serializable {
    public WorkspaceAccountPrincipal {
        if (workspaceId <= 0 || accountId <= 0) {
            throw new IllegalArgumentException("Workspace and account ids must be positive");
        }
    }

    @Override
    public String getName() {
        return Long.toString(accountId);
    }
}
