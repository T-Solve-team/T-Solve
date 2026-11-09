package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.ACME_CALLBACK;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.ADMIN_SECRET;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.BETA_CALLBACK;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.GOOGLE_CLIENT_ID;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.GOOGLE_CLIENT_SECRET;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.PROVISIONER_CLIENT_ID;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.REALM;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.TAG;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.WEB_SECRET;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.callbackUri;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.domainOf;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.logoutUri;
import static vn.tsolve.identity.keycloak.KeycloakProvisioningFixture.newSlug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner.ProvisionedObjects;

/**
 * The Keycloak adapter of the port against a real Keycloak 26.x started from the committed realm file (AC-001.57 to
 * .59, .61, .62). The adapter signs in as {@code tsolve-provisioner}; the test reads and arranges Keycloak with the
 * master admin. Each test uses its own slug, so order does not matter; the acme and beta objects of the import are the
 * "other Workspaces" that must stay untouched. On Docker 29 run with {@code -DargLine=-Dapi.version=1.44}.
 */
class KeycloakWorkspaceSignInProvisionerIT {

    /** The direct realm-management roles of tsolve-provisioner: the seven of Q4 plus manage-realm (S1), no others. */
    private static final List<String> PROVISIONER_ROLES = List.of(
            "manage-identity-providers",
            "view-identity-providers",
            "manage-clients",
            "view-clients",
            "manage-users",
            "view-users",
            "query-users",
            "manage-realm");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static KeycloakProvisioningFixture fixture;
    private static WorkspaceSignInProvisioner provisioner;

    @BeforeAll
    static void start() {
        fixture = new KeycloakProvisioningFixture();
        provisioner = fixture.adapter(fixture.newAdminClient());
    }

    @AfterAll
    static void stop() {
        if (fixture != null) {
            fixture.close();
        }
    }

    // ---- 1 ----

    @Test
    @DisplayName(
            "AC-001.58 provision creates the Organization, the hidden google-<slug> IdP linked to it and the two exact URIs")
    void createsOrganizationIdpAndUris_AC_001_58() {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        List<String> redirectBefore = fixture.redirectUris();
        List<String> logoutBefore = fixture.logoutUris();

        String organizationId = provisioner.provision(workspaceId, slug, domain).orElseThrow();

        List<OrganizationRepresentation> organizations = fixture.organizationsWithDomain(domain);
        assertThat(organizations).hasSize(1);
        OrganizationRepresentation organization = organizations.get(0);
        assertThat(organization.getId()).isEqualTo(organizationId);
        assertThat(organization.getAlias()).isEqualTo(slug);
        assertThat(organization.getDomains()).extracting(d -> d.getName()).containsExactly(domain);
        assertThat(fixture.realm()
                        .organizations()
                        .get(organizationId)
                        .toRepresentation()
                        .getAttributes())
                .containsEntry(TAG, List.of(workspaceId.toString()));

        IdentityProviderRepresentation idp =
                fixture.identityProvider("google-" + slug).orElseThrow();
        assertThat(idp.getProviderId()).isEqualTo("google");
        assertThat(idp.isHideOnLogin()).isTrue();
        assertThat(idp.getOrganizationId()).isEqualTo(organizationId);
        assertThat(idp.isTrustEmail()).isTrue();
        assertThat(idp.getFirstBrokerLoginFlowAlias()).isEqualTo("tsolve first broker login");
        assertThat(idp.getConfig())
                .containsEntry("clientId", GOOGLE_CLIENT_ID)
                .containsEntry("hostedDomain", domain)
                .containsEntry("syncMode", "IMPORT")
                .containsEntry(TAG, workspaceId.toString())
                .containsKey("clientSecret"); // Keycloak masks the stored secret on read
        List<IdentityProviderMapperRepresentation> mappers =
                fixture.realm().identityProviders().get(idp.getAlias()).getMappers();
        assertThat(mappers).anySatisfy(m -> {
            assertThat(m.getIdentityProviderMapper()).isEqualTo("oidc-user-attribute-idp-mapper");
            assertThat(m.getConfig()).containsEntry("claim", "hd").containsEntry("user.attribute", "hd");
        });

        List<String> redirectAfter = fixture.redirectUris();
        List<String> logoutAfter = fixture.logoutUris();
        assertThat(redirectAfter).containsExactlyInAnyOrderElementsOf(withAdded(redirectBefore, callbackUri(slug)));
        assertThat(logoutAfter).containsExactlyInAnyOrderElementsOf(withAdded(logoutBefore, logoutUri(slug)));
        assertThat(redirectAfter).contains(ACME_CALLBACK, BETA_CALLBACK).noneMatch(u -> u.contains("*"));
        assertThat(callbackUri(slug)).isEqualTo("http://" + slug + ".localhost:8080/api/v1/auth/callback");
        assertThat(logoutUri(slug)).isEqualTo("http://" + slug + ".localhost:8080/login");
        // N7: the full PUT of tsolve-web must not change the client's own secret
        assertThat(fixture.realm()
                        .clients()
                        .get(fixture.webClient().getId())
                        .getSecret()
                        .getValue())
                .isEqualTo(WEB_SECRET);
    }

    // ---- 2 ----

    @Test
    @DisplayName("AC-001.57 check reports which of the four Keycloak objects exist")
    void checkReportsEachObject_AC_001_57() {
        String slug = newSlug();
        UUID workspaceId = UUID.randomUUID();

        assertThat(provisioner.check(workspaceId)).isEqualTo(new ProvisionedObjects(false, false, false, false));

        provisioner.provision(workspaceId, slug, domainOf(slug));
        ProvisionedObjects all = provisioner.check(workspaceId);
        assertThat(all.organization()).isTrue();
        assertThat(all.identityProvider()).isTrue();
        assertThat(all.callbackUri()).isTrue();
        assertThat(all.logoutUri()).isTrue();

        fixture.realm().identityProviders().get("google-" + slug).remove();
        ProvisionedObjects withoutIdp = provisioner.check(workspaceId);
        assertThat(withoutIdp.organization()).isTrue();
        assertThat(withoutIdp.identityProvider()).isFalse();
        assertThat(withoutIdp.callbackUri()).isTrue();
        assertThat(withoutIdp.logoutUri()).isTrue();
    }

    // ---- 3 ----

    enum SecondCall {
        SAME_ARGUMENTS,
        OTHER_DOMAIN,
        OTHER_SLUG
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(SecondCall.class)
    @DisplayName(
            "AC-001.59 edge 108 provision twice for the same Workspace changes nothing and returns the same Organization id; another domain or slug throws and changes nothing")
    void provisionTwiceChangesNothing_AC_001_59_edge108(SecondCall secondCall) {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        String firstId = provisioner.provision(workspaceId, slug, domain).orElseThrow();
        KeycloakProvisioningFixture.Snapshot before = fixture.snapshot();

        switch (secondCall) {
            case SAME_ARGUMENTS -> {
                assertThat(provisioner.provision(workspaceId, slug, domain)).contains(firstId);
                assertThat(fixture.organizationsWithDomain(domain)).hasSize(1);
                assertThat(fixture.redirectUris().stream().filter(callbackUri(slug)::equals))
                        .hasSize(1);
                assertThat(fixture.logoutUris().stream().filter(logoutUri(slug)::equals))
                        .hasSize(1);
            }
            case OTHER_DOMAIN -> assertThatThrownBy(() -> provisioner.provision(workspaceId, slug, "other-" + domain))
                    .isInstanceOf(IllegalStateException.class);
            case OTHER_SLUG -> {
                String otherSlug = newSlug();
                assertThatThrownBy(() -> provisioner.provision(workspaceId, otherSlug, domain))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(fixture.identityProvider("google-" + otherSlug)).isEmpty();
            }
        }

        assertThat(fixture.snapshot()).isEqualTo(before);
        ProvisionedObjects objects = provisioner.check(workspaceId);
        assertThat(objects).isEqualTo(new ProvisionedObjects(true, true, true, true));
    }

    // ---- 4 ----

    @Test
    @DisplayName(
            "AC-001.62 edge 112 remove deletes the IdP, the two URIs, the Organization's users and the Organization, and only those")
    void removeDeletesOnlyThisWorkspace_AC_001_62_edge112() {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        String memberEmail = "dana@" + domain;
        String organizationId = provisioner.provision(workspaceId, slug, domain).orElseThrow();
        fixture.addOrganizationMember(organizationId, memberEmail);
        KeycloakProvisioningFixture.Snapshot withWorkspace = fixture.snapshot();
        assertThat(withWorkspace.users()).contains(memberEmail, "alice@acme.test", "bob@beta.test");

        try (KeycloakProvisioningFixture.CapturedLogs logs = new KeycloakProvisioningFixture.CapturedLogs()) {
            provisioner.remove(workspaceId);

            assertThat(fixture.organizationsWithDomain(domain)).isEmpty();
            assertThat(fixture.identityProvider("google-" + slug)).isEmpty();
            assertThat(fixture.redirectUris()).doesNotContain(callbackUri(slug)).contains(ACME_CALLBACK, BETA_CALLBACK);
            assertThat(fixture.logoutUris()).doesNotContain(logoutUri(slug));
            assertThat(fixture.usersWithEmail(memberEmail)).isEmpty();
            KeycloakProvisioningFixture.Snapshot afterRemove = fixture.snapshot();
            assertThat(afterRemove.users()).contains("alice@acme.test", "bob@beta.test");
            assertThat(afterRemove.organizations()).anyMatch(o -> o.startsWith("acme|"));
            assertThat(afterRemove.organizations()).anyMatch(o -> o.startsWith("beta|"));
            assertThat(afterRemove.identityProviders()).contains("google-acme", "google-beta");

            provisioner.remove(workspaceId);
            provisioner.remove(UUID.randomUUID());
            assertThat(fixture.snapshot()).isEqualTo(afterRemove);
            assertThat(provisioner.check(workspaceId)).isEqualTo(new ProvisionedObjects(false, false, false, false));

            assertThat(logs.all()).doesNotContain(memberEmail).doesNotContain(ADMIN_SECRET);
            assertThat(logs.all()).doesNotContain(GOOGLE_CLIENT_SECRET);
        }
    }

    // ---- 5 ----

    @Test
    @DisplayName(
            "AC-001.61 edge 109 tsolve-provisioner is a confidential service-account client with only the realm-management roles for identity providers, clients, organizations and users")
    void provisionerHasLeastRoles_AC_001_61_edge109() throws Exception {
        ClientRepresentation client = fixture.realm().clients().findByClientId(PROVISIONER_CLIENT_ID).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("the realm has no client " + PROVISIONER_CLIENT_ID));
        assertThat(client.isPublicClient()).isFalse();
        assertThat(client.isServiceAccountsEnabled()).isTrue();
        assertThat(client.isStandardFlowEnabled()).isFalse();
        assertThat(client.isImplicitFlowEnabled()).isFalse();
        assertThat(client.isDirectAccessGrantsEnabled()).isFalse();
        assertThat(client.getRedirectUris()).isNullOrEmpty();

        String serviceAccountUserId = fixture.realm()
                .clients()
                .get(client.getId())
                .getServiceAccountUser()
                .getId();
        String realmManagementId = fixture.realm()
                .clients()
                .findByClientId("realm-management")
                .get(0)
                .getId();
        Set<String> roles =
                fixture
                        .realm()
                        .users()
                        .get(serviceAccountUserId)
                        .roles()
                        .clientLevel(realmManagementId)
                        .listAll()
                        .stream()
                        .map(RoleRepresentation::getName)
                        .collect(Collectors.toSet());
        assertThat(roles).containsExactlyInAnyOrderElementsOf(PROVISIONER_ROLES);
        assertThat(roles).doesNotContain("realm-admin", "view-events", "manage-events", "manage-authorization");
        assertThat(fixture.realm()
                        .users()
                        .get(serviceAccountUserId)
                        .roles()
                        .realmLevel()
                        .listAll())
                .extracting(RoleRepresentation::getName)
                .doesNotContain("admin", "realm-admin");

        String file = Files.readString(KeycloakProvisioningFixture.REALM_FILE, StandardCharsets.UTF_8);
        assertThat(file).contains("${KEYCLOAK_ADMIN_CLIENT_SECRET}").doesNotContain(ADMIN_SECRET);

        // with its own token: a call outside its roles is forbidden
        HttpClient http = HttpClient.newHttpClient();
        String body = "grant_type=client_credentials&client_id=" + PROVISIONER_CLIENT_ID + "&client_secret="
                + URLEncoder.encode(ADMIN_SECRET, StandardCharsets.UTF_8);
        HttpResponse<String> token = http.send(
                HttpRequest.newBuilder(URI.create(
                                fixture.authServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(token.statusCode()).isEqualTo(200);
        JsonNode tokenJson = JSON.readTree(token.body());
        String bearer = "Bearer " + tokenJson.get("access_token").asText();
        // events are granted by neither the required roles nor manage-realm (Q4 note)
        HttpResponse<String> events = http.send(
                HttpRequest.newBuilder(URI.create(fixture.authServerUrl() + "/admin/realms/" + REALM + "/events"))
                        .header("Authorization", bearer)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(events.statusCode()).isEqualTo(403);
        // another realm: the token of tsolve opens nothing in master
        HttpResponse<String> master = http.send(
                HttpRequest.newBuilder(URI.create(fixture.authServerUrl() + "/admin/realms/master/clients"))
                        .header("Authorization", bearer)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(master.statusCode()).isIn(401, 403);
    }

    // ---- 6 (S6) ----

    enum Reprovision {
        OTHER_DOMAIN,
        SAME_DOMAIN_LINK_MISSING
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(Reprovision.class)
    @DisplayName(
            "AC-001.58 S6 provision after the Organization was deleted by hand throws and changes nothing when the surviving IdP has another domain or is not linked to an Organization")
    void reprovisionWithSurvivingIdpThrows_AC_001_58_S6(Reprovision reprovision) {
        String slug = newSlug();
        String domain = domainOf(slug);
        UUID workspaceId = UUID.randomUUID();
        String organizationId = provisioner.provision(workspaceId, slug, domain).orElseThrow();
        fixture.realm().organizations().get(organizationId).delete().close(); // a person in the admin console
        assertThat(fixture.organizationsWithDomain(domain)).isEmpty();
        assertThat(fixture.identityProvider("google-" + slug))
                .as("the IdP survives the deletion of its Organization")
                .isPresent();
        KeycloakProvisioningFixture.Snapshot before = fixture.snapshot();
        String askedDomain = reprovision == Reprovision.OTHER_DOMAIN ? "other-" + domain : domain;

        assertThatThrownBy(() -> provisioner.provision(workspaceId, slug, askedDomain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(fixture.snapshot()).isEqualTo(before);
        assertThat(fixture.organizationsWithDomain(domain)).isEmpty();
        assertThat(fixture.organizationsWithDomain(askedDomain)).isEmpty();
    }

    private static List<String> withAdded(List<String> list, String added) {
        List<String> copy = new java.util.ArrayList<>(list);
        copy.add(added);
        return copy;
    }
}
