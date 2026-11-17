package vn.tsolve.identity.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.port.ProvisioningWiring;

/**
 * At start-up of the {@code ops} process, adds {@code prompt=select_account} to the Google identity providers that
 * were provisioned before KAN-355. It changes nothing once they all have it. A Keycloak that cannot be reached is
 * logged and never stops the start-up.
 */
@Component
@Conditional(ProvisioningWiring.Configured.class)
public class GoogleIdpPromptBackfill implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleIdpPromptBackfill.class);

    private final KeycloakAdminClient client;

    public GoogleIdpPromptBackfill(KeycloakAdminClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = client.addAccountChooserToWorkspaceIdps();
            if (updated > 0) {
                LOG.info("Added the Google account chooser to {} Workspace identity provider(s)", updated);
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not add the Google account chooser to existing identity providers: {}", e.getMessage());
        }
    }
}
