package vn.tsolve.identity.signin.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tsolve.identity.signin.port.WorkspaceRef.Status;

/** KAN-327: the env-driven stand-in Workspace lookup for the non-dev profiles. */
class NoWorkspaceLookupTest {

    private static final String ACME_ID = "3f2b8c1e-5a4d-4e6f-9b7a-1c2d3e4f5a6b";
    private static final String BETA_ID = "7a1d9e2c-8b3f-4c5d-a6e7-0f1a2b3c4d5e";
    private static final String ACME = "acme.example.vn|acme|" + ACME_ID + "|acme.com|org-acme";
    private static final String BETA = "beta.example.vn|beta|" + BETA_ID + "|beta.com|org-beta";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", " ; "})
    @DisplayName("KAN-327 an empty setting knows no Workspace")
    void emptySettingKnowsNothing(String raw) {
        NoWorkspaceLookup lookup = new NoWorkspaceLookup(raw);

        assertThat(lookup.findBySiteHost("acme.example.vn")).isEmpty();
        assertThat(lookup.findById(UUID.fromString(ACME_ID))).isEmpty();
    }

    @Test
    @DisplayName("KAN-327 an entry becomes an ACTIVE Workspace found by host and by id")
    void entryBecomesActiveWorkspace() {
        NoWorkspaceLookup lookup = new NoWorkspaceLookup(ACME);

        WorkspaceRef acme = lookup.findBySiteHost("acme.example.vn").orElseThrow();
        assertThat(acme.id()).isEqualTo(UUID.fromString(ACME_ID));
        assertThat(acme.siteHost()).isEqualTo("acme.example.vn");
        assertThat(acme.slug()).isEqualTo("acme");
        assertThat(acme.googleDomain()).isEqualTo("acme.com");
        assertThat(acme.organizationId()).isEqualTo("org-acme");
        assertThat(acme.status()).isEqualTo(Status.ACTIVE);
        assertThat(lookup.findById(UUID.fromString(ACME_ID)).orElseThrow().slug())
                .isEqualTo("acme");
    }

    @Test
    @DisplayName("KAN-327 the host match ignores case and a port; other hosts find nothing")
    void hostMatchIgnoresCaseAndPort() {
        NoWorkspaceLookup lookup = new NoWorkspaceLookup(" Acme.Example.VN |acme|" + ACME_ID + "|acme.com|org-acme");

        assertThat(lookup.findBySiteHost("ACME.example.vn")).isPresent();
        assertThat(lookup.findBySiteHost("acme.example.vn:8443")).isPresent();
        assertThat(lookup.findBySiteHost("other.example.vn")).isEmpty();
        assertThat(lookup.findBySiteHost(null)).isEmpty();
        assertThat(lookup.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("KAN-327 a wrong field count fails at startup naming the entry index and host, not the rest")
    void wrongFieldCountFails() {
        assertThatThrownBy(() -> new NoWorkspaceLookup("beta.example.vn|beta|secret-part"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entry 1")
                .hasMessageContaining("beta.example.vn")
                .hasMessageNotContaining("secret-part");
    }

    @Test
    @DisplayName("KAN-327 a bad Workspace id fails at startup naming the entry index")
    void badUuidFails() {
        assertThatThrownBy(() -> new NoWorkspaceLookup("acme.example.vn|acme|not-a-uuid|acme.com|org-acme"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entry 1")
                .hasMessageContaining("acme.example.vn");
    }

    @Test
    @DisplayName("KAN-327 a blank field fails at startup naming the entry index")
    void blankFieldFails() {
        assertThatThrownBy(() -> new NoWorkspaceLookup("acme.example.vn|acme|" + ACME_ID + "||org-acme"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entry 1");
    }

    @Test
    @DisplayName("KAN-327 more than one entry fails at startup: all would share the one app database")
    void moreThanOneEntryFails() {
        assertThatThrownBy(() -> new NoWorkspaceLookup(ACME + ";" + BETA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one")
                .hasMessageNotContaining(ACME_ID);
        assertThatThrownBy(() -> new NoWorkspaceLookup(ACME + ";" + ACME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    @DisplayName("KAN-327 one entry with a trailing separator is accepted")
    void trailingSeparatorIsAccepted() {
        assertThat(new NoWorkspaceLookup(ACME + ";").findBySiteHost("acme.example.vn"))
                .isPresent();
    }
}
