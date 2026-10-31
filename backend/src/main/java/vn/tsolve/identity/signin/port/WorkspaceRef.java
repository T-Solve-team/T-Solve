package vn.tsolve.identity.signin.port;

import java.util.UUID;

public record WorkspaceRef(UUID id, String siteHost, String googleDomain, Status status) {

    public enum Status {
        ACTIVE,
        SUSPENDED
    }
}
