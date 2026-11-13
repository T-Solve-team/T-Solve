package vn.tsolve.identity.jiralink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.session.WebSession;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * The Jira link endpoints on the whole chain, with the real session filter and CSRF filter (AC-001.38, .71, .72, .75;
 * AC-002.5 for the logout that keeps the links).
 */
@TestPropertySource(properties = "TSOLVE_SITE_ORIGIN_TEMPLATE=http://{slug}.localhost:8080")
class JiraLinkControllerTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final String CONFIRM = "/api/v1/auth/jira-link/confirm";
    private static final String READ = "/api/v1/auth/jira-link/";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String JIRA_USER = "Nguyen Van A";
    private static final String SITE = "acme-corp";
    private static final String KEY_PREFIX = "tsolve:jiralink:";
    private static final String LAN = "lan@acme.example";
    private static final String MSG141_TEXT =
            "This sign-in request is not valid or has expired. Please start the sign-in again.";
    private static final String MSG07_TEXT = "Your session has expired. Please log in again.";

    @Autowired
    private WebSessionStore store;

    @Autowired
    private ObjectMapper json;

    @SpyBean
    private JiraLinkRequestService service;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void cleanTenants() {
        TwoTenantDatabases.tenantA().update("TRUNCATE jira_account_links");
        TwoTenantDatabases.tenantB().update("TRUNCATE jira_account_links");
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    // ---- helpers

    /** A signed-in ACME person (Lan): an Account in acme's database and a session for it; returns the cookie value. */
    private String acmeSession() {
        return acmeSession(LAN, "Lan");
    }

    private String acmeSession(String email, String fullName) {
        UUID account = UUID.randomUUID();
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at) VALUES (?, ?, ?, ?, now())",
                        account,
                        email,
                        fullName,
                        "ACTIVE");
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                ACME.id(),
                account,
                null,
                ACME.siteHost(),
                "id-token-hint",
                clock.instant(),
                clock.instant()));
    }

    private static UUID accountOf(String email) {
        return TwoTenantDatabases.tenantA()
                .queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
    }

    /** Every column of every Jira link row of one company database, in a fixed order. */
    private static List<Map<String, Object>> linkRows(JdbcTemplate db) {
        return db.queryForList("SELECT * FROM jira_account_links ORDER BY tracker_account_id, account_id");
    }

    private static Instant at(Object timestamp) {
        return ((Timestamp) timestamp).toInstant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Makes a link request through the service and returns its id. */
    private String requestFor(WorkspaceRef workspace) {
        String url = service.create(workspace.id(), "jira-acct-1", JIRA_USER, SITE);
        return url.substring(url.indexOf("request=") + "request=".length());
    }

    private String requestForTracker(String trackerAccountId) {
        String url = service.create(ACME.id(), trackerAccountId, JIRA_USER, SITE);
        return url.substring(url.indexOf("request=") + "request=".length());
    }

    private static String requestKey(String id) {
        return KEY_PREFIX + sha256Hex(id);
    }

    private boolean requestExists(String id) {
        return Boolean.TRUE.equals(redis.hasKey(requestKey(id)));
    }

    private Set<String> allKeys() {
        Set<String> keys = redis.keys("*");
        return keys == null ? Set.of() : new HashSet<>(keys);
    }

    private String body(Map<String, Object> fields) throws Exception {
        return json.writeValueAsString(fields);
    }

    private MockHttpServletRequestBuilder confirm(String host, String sessionCookie, String body, boolean validCsrf) {
        MockHttpServletRequestBuilder request = post(url(host, CONFIRM))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(body);
        if (sessionCookie != null) {
            request.cookie(new Cookie(SESSION_COOKIE, sessionCookie));
        }
        return validCsrf ? request.with(csrf()) : request;
    }

    private MockHttpServletResponse confirmAsAcme(String sessionCookie, String requestId) throws Exception {
        return perform(confirm(ACME.siteHost(), sessionCookie, body(Map.of("requestId", requestId)), true));
    }

    private MockHttpServletResponse read(String host, String sessionCookie, String requestId) throws Exception {
        MockHttpServletRequestBuilder request = get(url(host, READ + requestId));
        if (sessionCookie != null) {
            request.cookie(new Cookie(SESSION_COOKIE, sessionCookie));
        }
        return perform(request);
    }

    private JsonNode tree(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private void assertMsg141(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as("status").isEqualTo(400);
        assertThat(tree(response).path("code").asText()).as("code").isEqualTo("MSG141");
        assertThat(tree(response).path("detail").asText()).as("detail").isEqualTo(MSG141_TEXT);
    }

    private void assertMsg07(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as("status").isEqualTo(401);
        assertThat(tree(response).path("code").asText()).as("code").isEqualTo("MSG07");
        assertThat(tree(response).path("detail").asText()).as("detail").isEqualTo(MSG07_TEXT);
    }

    // ---- AC-001.72: the 400

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty body", "null id", "expired", "used", "other Workspace"})
    @DisplayName("AC-001.72 answers 400 MSG141 for a missing, expired, used or other-Workspace request")
    void answers400Msg141ForAMissingExpiredUsedOrOtherWorkspaceRequest(String row) throws Exception {
        String session = acmeSession();
        String body =
                switch (row) {
                    case "missing" -> "";
                    case "empty body" -> "{}";
                    case "null id" -> "{\"requestId\":null}";
                    case "expired" -> {
                        String id = requestFor(ACME);
                        redis.delete(requestKey(id)); // what Redis does when the 10 minutes are over
                        yield body(Map.of("requestId", id));
                    }
                    case "used" -> {
                        String id = requestFor(ACME);
                        assertThat(confirmAsAcme(session, id).getStatus()).isEqualTo(200);
                        yield body(Map.of("requestId", id));
                    }
                    default -> body(Map.of("requestId", requestFor(BETA)));
                };
        Set<String> keysBefore = allKeys();
        List<Map<String, Object>> aBefore = linkRows(TwoTenantDatabases.tenantA());
        List<Map<String, Object>> bBefore = linkRows(TwoTenantDatabases.tenantB());

        MockHttpServletResponse response = perform(confirm(ACME.siteHost(), session, body, true));

        assertMsg141(response);
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE))
                .as("session cookie")
                .isEmpty();
        assertThat(redis.hasKey(keyOf(session))).isTrue();
        assertThat(allKeys()).as("no other Redis key is created").isSubsetOf(keysBefore);
        assertThat(linkRows(TwoTenantDatabases.tenantA()))
                .as("no link is recorded")
                .isEqualTo(aBefore);
        assertThat(linkRows(TwoTenantDatabases.tenantB()))
                .as("no link is recorded in the other Workspace")
                .isEqualTo(bBefore);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "empty",
                "one char",
                "42 chars",
                "44 chars",
                "10000 chars",
                "star",
                "session key pattern",
                "path",
                "non-ASCII",
                "unknown well-formed",
                "malformed JSON"
            })
    @DisplayName("AC-001.72 edge 3 malformed or guessed ids answer 400 MSG141, never 500, and touch no key")
    void malformedOrGuessedIdsAnswer400Msg141(String row) throws Exception {
        String session = acmeSession();
        String existing = requestFor(ACME);
        String sent =
                switch (row) {
                    case "empty" -> "";
                    case "one char" -> "a";
                    case "42 chars" -> "a".repeat(42);
                    case "44 chars" -> "a".repeat(44);
                    case "10000 chars" -> "x".repeat(10_000);
                    case "star" -> "*";
                    case "session key pattern" -> "tsolve:session:*";
                    case "path" -> "../x";
                    case "non-ASCII" -> "é中文-id";
                    default -> SessionTestFixtures.randomCookieValue();
                };
        String body = row.equals("malformed JSON") ? "{not json" : body(Map.of("requestId", sent));
        Set<String> keysBefore = allKeys();

        MockHttpServletResponse response = perform(confirm(ACME.siteHost(), session, body, true));

        assertMsg141(response);
        assertThat(requestExists(existing))
                .as("the existing request is untouched")
                .isTrue();
        assertThat(allKeys()).as("no key is created or removed").isEqualTo(keysBefore);
        assertThat(linkRows(TwoTenantDatabases.tenantA()))
                .as("no link is recorded")
                .isEmpty();
        assertThat(linkRows(TwoTenantDatabases.tenantB()))
                .as("no link is recorded")
                .isEmpty();
    }

    // ---- AC-001.72: the 401 and the 403, and which one wins

    @ParameterizedTest
    @ValueSource(strings = {"no session", "idle session", "no csrf token", "wrong csrf token"})
    @DisplayName("AC-001.72 answers 401 MSG07 without a session and 403 without a CSRF token, keeping the request")
    void answers401Msg07WithoutASessionAnd403WithoutACsrfToken(String row) throws Exception {
        String session = acmeSession();
        String id = requestFor(ACME);
        String body = body(Map.of("requestId", id));
        MockHttpServletResponse response;
        switch (row) {
            case "no session" -> response = perform(confirm(ACME.siteHost(), null, body, true));
            case "idle session" -> {
                clock.advance(Duration.ofMinutes(61));
                response = perform(confirm(ACME.siteHost(), session, body, true));
            }
            case "no csrf token" -> response = perform(confirm(ACME.siteHost(), session, body, false));
            default ->
                response = perform(confirm(ACME.siteHost(), session, body, false)
                        .header("X-XSRF-TOKEN", "not-the-token")
                        .param("_csrf", "not-the-token"));
        }

        if (row.endsWith("csrf token")) {
            assertThat(response.getStatus()).isEqualTo(403);
        } else {
            assertMsg07(response);
        }
        if (row.equals("idle session")) {
            List<String> cookies = SignInTestFixtures.setCookie(response, SESSION_COOKIE);
            assertThat(cookies).hasSize(1);
            assertThat(cookies.get(0)).startsWith(SESSION_COOKIE + "=;");
        }
        assertThat(requestExists(id))
                .as("a refused POST never burns the request")
                .isTrue();
        assertThat(linkRows(TwoTenantDatabases.tenantA()))
                .as("no link is recorded")
                .isEmpty();
        assertThat(linkRows(TwoTenantDatabases.tenantB()))
                .as("no link is recorded")
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"no session, no csrf", "session, no csrf", "no session, csrf", "session, csrf"})
    @DisplayName("AC-001.72 edge 4 the checks run session, then CSRF token, then request (401, 403, 400)")
    void theChecksRunSessionThenCsrfThenRequest(String row) throws Exception {
        String session = acmeSession();
        String unknown = SessionTestFixtures.randomCookieValue();
        boolean signedIn = row.startsWith("session");
        boolean csrf = row.endsWith(", csrf");

        MockHttpServletResponse response =
                perform(confirm(ACME.siteHost(), signedIn ? session : null, body(Map.of("requestId", unknown)), csrf));

        if (!signedIn) {
            assertMsg07(response);
        } else if (!csrf) {
            assertThat(response.getStatus()).isEqualTo(403);
        } else {
            assertMsg141(response);
        }
        assertThat(linkRows(TwoTenantDatabases.tenantA()))
                .as("no link is recorded")
                .isEmpty();
        assertThat(linkRows(TwoTenantDatabases.tenantB()))
                .as("no link is recorded")
                .isEmpty();
    }

    // ---- AC-001.38: the Workspace and the tracker account come from the stored request only

    @Test
    @DisplayName("AC-001.38 takes the Workspace and the tracker account only from the stored request")
    void takesWorkspaceAndTrackerAccountOnlyFromTheStoredRequest() throws Exception {
        String session = acmeSession();
        String forBeta = requestFor(BETA);
        MockHttpServletRequestBuilder spoofed = post(url(ACME.siteHost(), CONFIRM + "?workspaceId=" + ACME.id()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(
                        Map.of("requestId", forBeta, "workspaceId", ACME.id().toString(), "trackerAccountId", "evil")))
                .cookie(new Cookie(SESSION_COOKIE, session))
                .cookie(new Cookie("workspace_id", ACME.id().toString()))
                .header("X-Workspace-Id", ACME.id().toString())
                .with(csrf());

        assertMsg141(perform(spoofed));

        String forAcme = requestFor(ACME);
        MockHttpServletResponse accepted = perform(confirm(
                ACME.siteHost(),
                session,
                body(Map.of(
                        "requestId",
                        forAcme,
                        "trackerAccountId",
                        "evil",
                        "workspaceId",
                        BETA.id().toString(),
                        "accountId",
                        UUID.randomUUID().toString())),
                true));
        assertThat(accepted.getStatus()).isEqualTo(200);

        // the row holds the stored tracker account and the session's Account, in the host's Workspace only
        List<Map<String, Object>> rows = linkRows(TwoTenantDatabases.tenantA());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("tracker_account_id")).isEqualTo("jira-acct-1");
        assertThat(rows.get(0).get("account_id")).isEqualTo(accountOf(LAN));
        assertThat(linkRows(TwoTenantDatabases.tenantB())).isEmpty();

        // the service saw only the request id and the Workspace of the host/session, never a client-sent value
        List<Invocation> consumes = Mockito.mockingDetails(service).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("consume"))
                .toList();
        assertThat(consumes).hasSize(2);
        assertThat(consumes.get(0).getArguments()).containsExactly(forBeta, ACME.id());
        assertThat(consumes.get(1).getArguments()).containsExactly(forAcme, ACME.id());
    }

    // ---- AC-001.71: the link is recorded

    @Test
    @DisplayName("AC-001.71 confirm records the link, answers 200 MSG75 and creates no new session")
    void confirmRecordsTheLinkAnswers200Msg75AndCreatesNoNewSession() throws Exception {
        String session = acmeSession();
        String id = requestFor(ACME);
        Set<String> keysBefore = allKeys();
        assertThat(keysBefore).contains(keyOf(session), requestKey(id));

        MockHttpServletResponse response = confirmAsAcme(session, id);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tree(response).path("code").asText()).isEqualTo("MSG75");
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE))
                .as("no new session")
                .isEmpty();
        Set<String> expected = new HashSet<>(keysBefore);
        expected.remove(requestKey(id));
        assertThat(allKeys()).as("only the request key is gone").isEqualTo(expected);
        assertThat(redis.hasKey(keyOf(session))).isTrue();
        List<Map<String, Object>> rows = linkRows(TwoTenantDatabases.tenantA());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("tracker_account_id")).isEqualTo("jira-acct-1");
        assertThat(rows.get(0).get("account_id")).isEqualTo(accountOf(LAN));
        assertThat(at(rows.get(0).get("linked_at"))).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MILLIS));
        assertThat(linkRows(TwoTenantDatabases.tenantB())).isEmpty();

        assertMsg141(confirmAsAcme(session, id));
        assertThat(linkRows(TwoTenantDatabases.tenantA()))
                .as("a second confirm adds no row")
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-001.71 a confirm from another Account replaces the earlier link of the same tracker account")
    void aConfirmFromAnotherAccountReplacesTheEarlierLink() throws Exception {
        String lan = acmeSession();
        assertThat(confirmAsAcme(lan, requestFor(ACME)).getStatus()).isEqualTo(200);
        String minh = acmeSession("minh@acme.example", "Minh");

        MockHttpServletResponse second = confirmAsAcme(minh, requestFor(ACME));

        assertThat(second.getStatus()).isEqualTo(200);
        List<Map<String, Object>> rows = linkRows(TwoTenantDatabases.tenantA());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("tracker_account_id")).isEqualTo("jira-acct-1");
        assertThat(rows.get(0).get("account_id")).isEqualTo(accountOf("minh@acme.example"));
    }

    @Test
    @DisplayName(
            "AC-001.71 edge 3 a failure while recording answers 500 MSG06, keeps the request used up and records nothing")
    void aFailureWhileRecordingAnswers500Msg06AndRecordsNothing() throws Exception {
        String session = acmeSession();
        String id = requestFor(ACME);
        // a trigger makes the insert fail inside the database, whatever way the code writes the row
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        a.execute("CREATE OR REPLACE FUNCTION fail_link_insert() RETURNS trigger AS $$ BEGIN "
                + "RAISE EXCEPTION 'secret sql failure'; END $$ LANGUAGE plpgsql");
        a.execute("CREATE TRIGGER fail_link_insert BEFORE INSERT OR UPDATE ON jira_account_links "
                + "FOR EACH ROW EXECUTE FUNCTION fail_link_insert()");
        MockHttpServletResponse response;
        try {
            response = confirmAsAcme(session, id);
        } finally {
            a.execute("DROP TRIGGER IF EXISTS fail_link_insert ON jira_account_links");
        }

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(tree(response).path("code").asText()).isEqualTo("MSG06");
        assertThat(response.getContentAsString()).doesNotContain("secret sql").doesNotContain("jira_account_links");
        assertThat(requestExists(id)).as("the request stays used up").isFalse();
        assertThat(linkRows(TwoTenantDatabases.tenantA())).isEmpty();
        assertThat(linkRows(TwoTenantDatabases.tenantB())).isEmpty();
    }

    @Test
    @DisplayName("AC-001.71 a new confirm of the same tracker account by the same Account refreshes linked_at")
    void aNewConfirmBySameAccountRefreshesLinkedAt() throws Exception {
        String session = acmeSession();
        assertThat(confirmAsAcme(session, requestFor(ACME)).getStatus()).isEqualTo(200);
        Instant first = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        clock.advance(Duration.ofMinutes(5));

        assertThat(confirmAsAcme(session, requestFor(ACME)).getStatus()).isEqualTo(200);

        List<Map<String, Object>> rows = linkRows(TwoTenantDatabases.tenantA());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("account_id")).isEqualTo(accountOf(LAN));
        assertThat(at(rows.get(0).get("linked_at")))
                .isEqualTo(first.plus(Duration.ofMinutes(5)))
                .isAfter(first);
    }

    @Test
    @DisplayName("AC-001.71 edge 2 one Account keeps several links, and relinking one leaves the others")
    void oneAccountKeepsSeveralLinks() throws Exception {
        String lan = acmeSession();
        String minh = acmeSession("minh@acme.example", "Minh");
        assertThat(confirmAsAcme(lan, requestForTracker("jira-acct-1")).getStatus())
                .isEqualTo(200);
        assertThat(confirmAsAcme(lan, requestForTracker("jira-acct-2")).getStatus())
                .isEqualTo(200);
        assertThat(linkRows(TwoTenantDatabases.tenantA())).hasSize(2);

        assertThat(confirmAsAcme(minh, requestForTracker("jira-acct-1")).getStatus())
                .isEqualTo(200);

        List<Map<String, Object>> rows = linkRows(TwoTenantDatabases.tenantA());
        assertThat(rows)
                .extracting(row -> row.get("tracker_account_id"), row -> row.get("account_id"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("jira-acct-1", accountOf("minh@acme.example")),
                        org.assertj.core.groups.Tuple.tuple("jira-acct-2", accountOf(LAN)));
    }

    // ---- AC-002.5: logout keeps the links

    @Test
    @DisplayName("AC-002.5 logout keeps the Account's Jira links")
    void logoutKeepsTheAccountsJiraLinks() throws Exception {
        String session = acmeSession();
        assertThat(confirmAsAcme(session, requestFor(ACME)).getStatus()).isEqualTo(200);
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO jira_account_links (tracker_account_id, account_id, linked_at) VALUES (?, ?, ?)",
                        "jira-acct-2",
                        accountOf(LAN),
                        Timestamp.from(clock.instant()));
        List<Map<String, Object>> before = linkRows(TwoTenantDatabases.tenantA());
        assertThat(before).hasSize(2);

        MockHttpServletResponse response =
                perform(post(url(ACME.siteHost(), LOGOUT)).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session)));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(redis.hasKey(keyOf(session))).as("the session is gone").isFalse();
        assertThat(linkRows(TwoTenantDatabases.tenantA())).isEqualTo(before);
    }

    // ---- AC-001.75: the GET

    @Test
    @DisplayName(
            "AC-001.75 GET returns the Jira user and site name and leaves the request usable; fails like AC-001.72")
    void getReturnsTheTwoNamesAndLeavesTheRequestUsable() throws Exception {
        String session = acmeSession();
        String id = requestFor(ACME);

        MockHttpServletResponse first = read(ACME.siteHost(), session, id);
        MockHttpServletResponse second = read(ACME.siteHost(), session, id);

        for (MockHttpServletResponse response : List.of(first, second)) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(tree(response).path("jiraUserName").asText()).isEqualTo(JIRA_USER);
            assertThat(tree(response).path("jiraSiteName").asText()).isEqualTo(SITE);
        }
        assertThat(requestExists(id)).as("the GET does not consume").isTrue();
        assertThat(confirmAsAcme(session, id).getStatus())
                .as("the confirm still finds it")
                .isEqualTo(200);

        // 400 rows: unknown, used (just confirmed), other Workspace
        assertMsg141(read(ACME.siteHost(), session, SessionTestFixtures.randomCookieValue()));
        assertMsg141(read(ACME.siteHost(), session, id));
        String forBeta = requestFor(BETA);
        assertMsg141(read(ACME.siteHost(), session, forBeta));

        // 401 row: no session
        String another = requestFor(ACME);
        assertMsg07(read(ACME.siteHost(), null, another));
        assertThat(requestExists(another)).isTrue();
    }
}
