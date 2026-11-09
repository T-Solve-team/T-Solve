package vn.tsolve.identity.signin.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Creates, checks and removes the Keycloak objects of a Workspace's sign-in (AC-001.57). The platform's
 * {@code WorkspaceProvisioning} is the caller. {@code check} and {@code remove} get only the Workspace id; the
 * adapter finds the objects by the tag that {@code provision} put on them.
 */
public interface WorkspaceSignInProvisioner {

    /**
     * Creates the Organization, the Google identity provider and the two {@code tsolve-web} URIs of the Workspace.
     *
     * @param siteName the {@code <slug>} of the Workspace site
     * @return the Keycloak Organization id, or empty when nothing is provisioned (the no-op)
     */
    Optional<String> provision(UUID workspaceId, String siteName, String googleDomain);

    /** Reports which of the four Keycloak objects exist for the Workspace. */
    ProvisionedObjects check(UUID workspaceId);

    /** Deletes every Keycloak object of the Workspace; succeeds and changes nothing when there are none. */
    void remove(UUID workspaceId);

    record ProvisionedObjects(boolean organization, boolean identityProvider, boolean callbackUri, boolean logoutUri) {}
}
