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
import java.time.Duration;
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
 * The Jira link endpoints on the whole chain, with the real session filter and CSRF filter (AC-001.38, .72, .75).
 * Task 30a answers 501 after it has consumed a valid request: the account link itself is Task 30b.
 */
@TestPropertySource(properties = "TSOLVE_SITE_ORIGIN_TEMPLATE=http://{slug}.localhost:8080")
class JiraLinkControllerTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final String CONFIRM = "/api/v1/auth/jira-link/confirm";
    private static final String READ = "/api/v1/auth/jira-link/";
    private static final String JIRA_USER = "Nguyen Van A";
    private static final String SITE = "acme-corp";
    private static final String KEY_PREFIX = "tsolve:jiralink:";
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
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    // ---- helpers

    /** A signed-in ACME person: an Account in acme's database and a session for it; returns the cookie value. */
    private String acmeSession() {
        UUID account = UUID.randomUUID();
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at) VALUES (?, ?, ?, ?, now())",
                        account,
                        "lan@acme.example",
                        "Lan",
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

    /** Makes a link request through the service and returns its id. */
    private String requestFor(WorkspaceRef workspace) {
        String url = service.create(workspace.id(), "jira-acct-1", JIRA_USER, SITE);
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
                        assertThat(confirmAsAcme(session, id).getStatus()).isEqualTo(501);
                        yield body(Map.of("requestId", id));
                    }
                    default -> body(Map.of("requestId", requestFor(BETA)));
                };
        Set<String> keysBefore = allKeys();

        MockHttpServletResponse response = perform(confirm(ACME.siteHost(), session, body, true));

        assertMsg141(response);
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE))
                .as("session cookie")
                .isEmpty();
        assertThat(redis.hasKey(keyOf(session))).isTrue();
        assertThat(allKeys()).as("no other Redis key is created").isSubsetOf(keysBefore);
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
                        BETA.id().toString())),
                true));
        assertThat(accepted.getStatus()).isEqualTo(501);

        // the service saw only the request id and the Workspace of the host/session, never a client-sent value
        List<Invocation> consumes = Mockito.mockingDetails(service).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("consume"))
                .toList();
        assertThat(consumes).hasSize(2);
        assertThat(consumes.get(0).getArguments()).containsExactly(forBeta, ACME.id());
        assertThat(consumes.get(1).getArguments()).containsExactly(forAcme, ACME.id());
    }

    // ---- AC-001.72: the 501 of Task 30a

    @Test
    @DisplayName("AC-001.72 a valid request answers 501, uses the request up and records nothing until Task 30b")
    void aValidRequestAnswers501AndRecordsNothingUntilTask30b() throws Exception {
        String session = acmeSession();
        String id = requestFor(ACME);

        MockHttpServletResponse response = confirmAsAcme(session, id);

        assertThat(response.getStatus()).isEqualTo(501);
        assertThat(SignInTestFixtures.setCookie(response, SESSION_COOKIE))
                .as("no new session")
                .isEmpty();
        assertThat(requestExists(id)).as("the request is consumed").isFalse();
        assertMsg141(confirmAsAcme(session, id));
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
                .isEqualTo(501);

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
