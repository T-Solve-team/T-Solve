package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleIdpPromptBackfillTest {

    private final KeycloakAdminClient client = mock(KeycloakAdminClient.class);

    @Test
    @DisplayName("BUG-322 start-up asks the client to backfill the account chooser")
    void runsTheBackfill_BUG_322() {
        when(client.addAccountChooserToWorkspaceIdps()).thenReturn(2);

        new GoogleIdpPromptBackfill(client).run(null);

        verify(client).addAccountChooserToWorkspaceIdps();
    }

    @Test
    @DisplayName("BUG-322 an unreachable Keycloak never stops the start-up")
    void keycloakDownDoesNotFailStartup_BUG_322() {
        when(client.addAccountChooserToWorkspaceIdps()).thenThrow(new IllegalStateException("Keycloak is down"));

        assertThatCode(() -> new GoogleIdpPromptBackfill(client).run(null)).doesNotThrowAnyException();
    }
}
