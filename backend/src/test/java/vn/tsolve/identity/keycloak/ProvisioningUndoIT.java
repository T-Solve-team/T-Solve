package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.ADMIN_SECRET;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.GOOGLE_CLIENT_SECRET;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.domainOf;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.newSlug;

import ch.qos.logback.classic.Level;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner.ProvisionedObjects;

/**
 * A failure after the Organization and the IdP exist (AC-001.59, edge 108). The failure is injected into a Mockito
 * spy of the real {@link KeycloakAdminClient}: its URI write throws once. On Docker 29 run with
 * {@code -DargLine=-Dapi.version=1.44}.
 */
class ProvisioningUndoIT {

    private static KeycloakProvisioningFixture fixture;

    @BeforeAll
    static void start() {
        fixture = new KeycloakProvisioningFixture();
    }

    @AfterAll
    static void stop() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    @DisplayName(
            "AC-001.59 edge 108 a failure after the Organization and the IdP exist deletes them in reverse order and throws, and a later remove changes nothing")
    void failureMidwayLeavesNothing_AC_001_59_edge108() {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        KeycloakAdminClient client = spy(fixture.newAdminClient());
        // the write of the URIs fails once; a later call (an undo that wrote again) would use the real method
        doThrow(new IllegalStateException("simulated failure of the URI write"))
                .doCallRealMethod()
                .when(client)
                .writeWebClientUris(any());
        WorkspaceSignInProvisioner provisioner = fixture.adapter(client);
        KeycloakProvisioningFixture.Snapshot before = fixture.snapshot();

        try (KeycloakProvisioningFixture.CapturedLogs logs = new KeycloakProvisioningFixture.CapturedLogs()) {
            assertThatThrownBy(() -> provisioner.provision(workspaceId, slug, domain))
                    .isInstanceOf(RuntimeException.class)
                    .satisfies(e -> assertThat(String.valueOf(e.getMessage()))
                            .doesNotContain(ADMIN_SECRET)
                            .doesNotContain(GOOGLE_CLIENT_SECRET));

            InOrder order = inOrder(client);
            order.verify(client).deleteIdentityProvider("google-" + slug);
            order.verify(client).deleteOrganization(anyString());

            assertThat(fixture.organizationsWithDomain(domain)).isEmpty();
            assertThat(fixture.identityProvider("google-" + slug)).isEmpty();
            assertThat(fixture.snapshot()).isEqualTo(before);
            assertThat(provisioner.check(workspaceId)).isEqualTo(new ProvisionedObjects(false, false, false, false));

            // the platform's undo of the Workspace afterwards
            provisioner.remove(workspaceId);
            assertThat(fixture.snapshot()).isEqualTo(before);

            assertThat(logs.all()).doesNotContain(ADMIN_SECRET).doesNotContain(GOOGLE_CLIENT_SECRET);
        }
    }

    @Test
    @DisplayName(
            "AC-001.59 S2 a failing undo step does not skip the later steps: the Organization is deleted, the original failure is thrown with the undo failure suppressed, and an ERROR names the step and the Workspace")
    void failingUndoStepDoesNotSkipTheRest_AC_001_59_S2() {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        KeycloakAdminClient client = spy(fixture.newAdminClient());
        doThrow(new IllegalStateException("simulated failure of the URI write"))
                .doCallRealMethod()
                .when(client)
                .writeWebClientUris(any());
        doThrow(new IllegalStateException("simulated failure of the IdP delete"))
                .doCallRealMethod()
                .when(client)
                .deleteIdentityProvider(anyString());
        WorkspaceSignInProvisioner provisioner = fixture.adapter(client);
        KeycloakProvisioningFixture.Snapshot before = fixture.snapshot();

        try (KeycloakProvisioningFixture.CapturedLogs logs = new KeycloakProvisioningFixture.CapturedLogs()) {
            Throwable thrown = catchThrowable(() -> provisioner.provision(workspaceId, slug, domain));

            assertThat(thrown)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("simulated failure of the URI write");
            assertThat(thrown.getSuppressed())
                    .as("the undo failure is attached to the original failure")
                    .anySatisfy(s -> assertThat(s).hasMessage("simulated failure of the IdP delete"));
            assertThat(fixture.organizationsWithDomain(domain))
                    .as("the Organization is deleted although the IdP step failed")
                    .isEmpty();
            assertThat(logs.lines(Level.ERROR))
                    .as("an ERROR names the failed step and the Workspace")
                    .anySatisfy(line -> assertThat(line)
                            .containsIgnoringCase("identity provider")
                            .contains(workspaceId.toString()));
            assertThat(logs.all()).doesNotContain(ADMIN_SECRET).doesNotContain(GOOGLE_CLIENT_SECRET);

            // the platform's later remove finishes the job the failed step left
            provisioner.remove(workspaceId);
            assertThat(fixture.snapshot()).isEqualTo(before);
        }
    }
}
