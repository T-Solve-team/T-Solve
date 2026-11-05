package vn.tsolve.identity.session;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Trusted session-issuer metadata; a request parameter or header never creates this binding. */
public record WorkspaceSessionBinding(UUID workspaceId) implements Serializable {
    public WorkspaceSessionBinding {
        Objects.requireNonNull(workspaceId, "workspaceId");
    }
}
