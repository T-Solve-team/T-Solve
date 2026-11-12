package vn.tsolve.identity.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/** Test-only base of the Task 27b tests: real Redis, one ACME Account, the recorder and small session helpers. */
@Import({WhoAmITestController.class, SessionEndedRecorder.class})
abstract class AbstractExpiryIT extends AbstractSessionIT {

    protected static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    protected static final String WHOAMI = "/api/v1/test/whoami";
    protected static final String HINT = "id-token-hint-of-acme-session";

    @Autowired
    protected WebSessionStore store;

    @Autowired
    protected ObjectMapper json;

    protected UUID account;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void seedAccount() {
        WhoAmITestController.reset();
        SessionEndedRecorder.reset();
        account = UUID.randomUUID();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at)"
                                + " VALUES (?, ?, 'Lan', 'ACTIVE', now())",
                        account,
                        "lan@acme.example");
    }

    /** An ACME Workspace session for the seeded Account, created and last active at the given instants. */
    protected String acmeSession(Instant createdAt, Instant lastActivityAt) {
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE, ACME.id(), account, null, ACME.siteHost(), HINT, createdAt, lastActivityAt));
    }

    /** An ACME session created now by the movable clock. */
    protected String acmeSession() {
        return acmeSession(clock.instant(), clock.instant());
    }

    protected void moveClockTo(Instant target) {
        clock.advance(Duration.between(clock.instant(), target));
    }

    protected MockHttpServletResponse whoami(String cookie) throws Exception {
        return perform(get(url(ACME.siteHost(), WHOAMI)).cookie(new Cookie(SESSION_COOKIE, cookie)));
    }

    /** A request the session must not renew (AC-001.25). */
    protected MockHttpServletResponse backgroundWhoami(String cookie) throws Exception {
        return perform(get(url(ACME.siteHost(), WHOAMI))
                .cookie(new Cookie(SESSION_COOKIE, cookie))
                .header(WebSessionFilter.BACKGROUND_HEADER, "1"));
    }

    protected String codeOf(MockHttpServletResponse response) throws Exception {
        JsonNode body = json.readTree(response.getContentAsString());
        return body.path("code").asText();
    }
}
