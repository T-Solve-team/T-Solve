package vn.tsolve.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.session.WebSession;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * The session filter runs before the CSRF filter, so a signed-in POST without a CSRF token is refused as a CSRF
 * failure (403), not as "nobody is signed in" (401) (AC-001.72, be-foundation AC-002.11).
 */
class SecurityConfigCsrfOrderTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;

    @Autowired
    private WebSessionStore store;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @Test
    @DisplayName("AC-001.72 a signed-in POST with no CSRF token answers 403, not 401")
    void aSignedInPostWithNoCsrfTokenAnswers403() throws Exception {
        UUID account = UUID.randomUUID();
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO app_user (id, email, full_name, status, created_at) VALUES (?, ?, ?, ?, now())",
                        account,
                        "lan@acme.example",
                        "Lan",
                        "ACTIVE");
        String session = store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                ACME.id(),
                account,
                null,
                ACME.siteHost(),
                "id-token-hint",
                clock.instant(),
                clock.instant()));

        MockHttpServletResponse response = perform(post(url(ACME.siteHost(), "/api/v1/test/anything"))
                .cookie(new Cookie(SESSION_COOKIE, session))
                .contentType("application/json")
                .content("{}"));

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
