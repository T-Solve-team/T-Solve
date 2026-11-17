package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The staging realm files build the staging Google identity providers; a reset must not lose the account chooser. */
class StagingRealmFilesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("BUG-322 the staging Google identity providers keep prompt=select_account")
    void stagingGoogleIdpsAskForTheAccountChooser_BUG_322() throws IOException {
        for (String file : new String[] {"realm-tsolve.json", "realm-tsolve-ops.json"}) {
            Path path =
                    Path.of("../infra/keycloak/staging", file).toAbsolutePath().normalize();
            JsonNode providers = JSON.readTree(path.toFile()).path("identityProviders");

            int google = 0;
            for (JsonNode idp : providers) {
                if ("google".equals(idp.path("providerId").asText())) {
                    google++;
                    assertThat(idp.path("config").path("prompt").asText())
                            .as("%s %s", file, idp.path("alias").asText())
                            .isEqualTo("select_account");
                }
            }
            assertThat(google).as("%s has a Google identity provider", file).isPositive();
        }
    }
}
