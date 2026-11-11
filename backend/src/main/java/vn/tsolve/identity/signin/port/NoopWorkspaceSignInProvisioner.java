package vn.tsolve.identity.signin.port;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * The port's answer while the Keycloak adapter is not wired (AC-001.60): it creates nothing, reports nothing and holds
 * no client, so a Workspace web process starts without any Keycloak Admin or Google value.
 */
@Component
@Conditional(ProvisioningWiring.NotConfigured.class)
public class NoopWorkspaceSignInProvisioner implements WorkspaceSignInProvisioner {

    public NoopWorkspaceSignInProvisioner() {}

    @Override
    public Optional<String> provision(UUID workspaceId, String siteName, String googleDomain) {
        return Optional.empty();
    }

    @Override
    public ProvisionedObjects check(UUID workspaceId) {
        return new ProvisionedObjects(false, false, false, false);
    }

    @Override
    public void remove(UUID workspaceId) {
        // nothing was created
    }
}
