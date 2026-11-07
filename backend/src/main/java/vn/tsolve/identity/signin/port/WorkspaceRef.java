package vn.tsolve.identity.signin.port;

import java.util.UUID;

/**
 * A Workspace as the sign-in sees it. {@code slug} is never null; a null {@code organizationId} means the Workspace
 * has no Organization (the record does not validate this).
 */
public record WorkspaceRef(
        UUID id, String siteHost, String googleDomain, Status status, String slug, String organizationId) {

    public enum Status {
        ACTIVE,
        SUSPENDED
    }
}
