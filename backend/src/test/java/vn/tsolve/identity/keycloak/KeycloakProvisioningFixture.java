package vn.tsolve.identity.keycloak;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Test-only support of the three Keycloak provisioning ITs (AC-001.57 to .62): one real Keycloak with the committed
 * realm file, one PostgreSQL for the advisory lock, and the master-admin view the tests use to arrange and read
 * Keycloak. Every IT class owns its own instance, never shared with {@code RealmImportIT}. On Docker 29 run with
 * {@code -DargLine=-Dapi.version=1.44} (never committed).
 */
final class KeycloakProvisioningFixture implements AutoCloseable {

    static final String REALM = "tsolve";
    static final String WEB_CLIENT_ID = "tsolve-web";
    static final String PROVISIONER_CLIENT_ID = "tsolve-provisioner";
    static final String ADMIN_SECRET = "it-keycloak-admin-client-secret";
    static final String WEB_SECRET = "it-keycloak-web-client-secret";
    static final String GOOGLE_CLIENT_ID = "it-google-client";
    static final String GOOGLE_CLIENT_SECRET = "it-google-secret";
    static final String SITE_ORIGIN_TEMPLATE = "http://{slug}.localhost:8080";
    static final String TAG = "tsolve.workspace-id";
    static final Path REALM_FILE =
            Path.of("../infra/keycloak/realm-tsolve.json").toAbsolutePath().normalize();
    static final String ACME_CALLBACK = "http://acme.localhost:8080/api/v1/auth/callback";
    static final String BETA_CALLBACK = "http://beta.localhost:8080/api/v1/auth/callback";

    private final KeycloakContainer keycloak;
    private final PostgreSQLContainer<?> postgres;
    private final DriverManagerDataSource dataSource;

    KeycloakProvisioningFixture() {
        keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4.7")
                .withEnv("KEYCLOAK_WEB_CLIENT_SECRET", WEB_SECRET)
                .withEnv("KEYCLOAK_ADMIN_CLIENT_SECRET", ADMIN_SECRET)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(REALM_FILE), "/opt/keycloak/data/import/realm-tsolve.json");
        postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
        keycloak.start();
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Override
    public void close() {
        keycloak.stop();
        postgres.stop();
    }

    // ---- what the adapter is built from ----

    KeycloakProvisioningProperties properties() {
        return new KeycloakProvisioningProperties(
                keycloak.getAuthServerUrl(),
                REALM,
                ADMIN_SECRET,
                GOOGLE_CLIENT_ID,
                GOOGLE_CLIENT_SECRET,
                SITE_ORIGIN_TEMPLATE);
    }

    KeycloakAdminClient newAdminClient() {
        return new KeycloakAdminClient(properties());
    }

    DataSource dataSource() {
        return dataSource;
    }

    KeycloakWorkspaceSignInProvisioner adapter(KeycloakAdminClient client) {
        return new KeycloakWorkspaceSignInProvisioner(client, new ProvisioningLock(dataSource), properties());
    }

    // ---- names a test uses ----

    static String newSlug() {
        return "gamma-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    static String domainOf(String slug) {
        return slug + ".test";
    }

    static String callbackUri(String slug) {
        return SITE_ORIGIN_TEMPLATE.replace("{slug}", slug) + "/api/v1/auth/callback";
    }

    static String logoutUri(String slug) {
        return SITE_ORIGIN_TEMPLATE.replace("{slug}", slug) + "/login";
    }

    // ---- Keycloak as the master admin sees it (no lock, like a person in the admin console) ----

    String authServerUrl() {
        return keycloak.getAuthServerUrl();
    }

    RealmResource realm() {
        return keycloak.getKeycloakAdminClient().realm(REALM);
    }

    ClientRepresentation webClient() {
        List<ClientRepresentation> found = realm().clients().findByClientId(WEB_CLIENT_ID);
        if (found.size() != 1) {
            throw new AssertionError("the realm must have exactly one client " + WEB_CLIENT_ID);
        }
        return found.get(0);
    }

    List<String> redirectUris() {
        List<String> uris = webClient().getRedirectUris();
        return uris == null ? List.of() : uris;
    }

    List<String> logoutUris() {
        String raw = webClient().getAttributes().get("post.logout.redirect.uris");
        return raw == null || raw.isBlank() ? List.of() : Arrays.asList(raw.split("##"));
    }

    /** Overwrites both URI lists of tsolve-web, as a person editing the client in the admin console would. */
    void setWebClientUris(List<String> redirect, List<String> logout) {
        ClientRepresentation client = webClient();
        client.setRedirectUris(new ArrayList<>(redirect));
        client.getAttributes().put("post.logout.redirect.uris", String.join("##", logout));
        realm().clients().get(client.getId()).update(client);
    }

    List<OrganizationRepresentation> organizationsWithDomain(String domain) {
        return realm().organizations().getAll().stream()
                .filter(o -> o.getDomains().stream().anyMatch(d -> domain.equals(d.getName())))
                .toList();
    }

    Optional<IdentityProviderRepresentation> identityProvider(String alias) {
        return realm().identityProviders().findAll().stream()
                .filter(p -> alias.equals(p.getAlias()))
                .findFirst();
    }

    /** Creates a Keycloak user and makes it a member of the Organization. */
    String addOrganizationMember(String organizationId, String email) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setEnabled(true);
        String userId = CreatedResponseUtil.getCreatedId(realm().users().create(user));
        realm().organizations().get(organizationId).members().addMember(userId).close();
        return userId;
    }

    List<UserRepresentation> usersWithEmail(String email) {
        return realm().users().searchByEmail(email, true);
    }

    /** Everything a test compares before and after a call that must change nothing. */
    Snapshot snapshot() {
        return new Snapshot(
                realm().organizations().getAll().stream()
                        .map(o -> o.getAlias() + "|"
                                + o.getDomains().stream()
                                        .map(d -> d.getName())
                                        .sorted()
                                        .collect(Collectors.joining(",")))
                        .collect(Collectors.toCollection(TreeSet::new)),
                realm().identityProviders().findAll().stream()
                        .map(IdentityProviderRepresentation::getAlias)
                        .collect(Collectors.toCollection(TreeSet::new)),
                realm().users().list().stream()
                        .filter(u -> u.getServiceAccountClientId() == null)
                        .map(UserRepresentation::getUsername)
                        .collect(Collectors.toCollection(TreeSet::new)),
                new TreeSet<>(redirectUris()),
                new TreeSet<>(logoutUris()));
    }

    record Snapshot(
            Set<String> organizations,
            Set<String> identityProviders,
            Set<String> users,
            Set<String> redirectUris,
            Set<String> logoutUris) {}

    // ---- captured log of the adapter ----

    /** Collects what the identity module logs while open. */
    static final class CapturedLogs implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger("vn.tsolve.identity");
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        CapturedLogs() {
            appender.start();
            logger.addAppender(appender);
        }

        /** Formatted message and the messages of the exception chain of every event, one string per event. */
        List<String> lines() {
            return appender.list.stream().map(CapturedLogs::render).toList();
        }

        List<String> lines(Level level) {
            return appender.list.stream()
                    .filter(e -> e.getLevel().equals(level))
                    .map(CapturedLogs::render)
                    .toList();
        }

        String all() {
            return String.join("\n", lines());
        }

        private static String render(ILoggingEvent event) {
            StringBuilder text = new StringBuilder(event.getFormattedMessage());
            for (var proxy = event.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
                text.append(" | ").append(proxy.getMessage());
            }
            return text.toString();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
