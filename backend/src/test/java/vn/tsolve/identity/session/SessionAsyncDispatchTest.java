package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * AC-001.16 (a passing sign-in gives a signed-in session): the session user must also survive the ASYNC re-dispatch of
 * a request (Callable, DeferredResult, SseEmitter), where the web chain reloads the context from the request.
 */
@Import(AsyncDispatchTestController.class)
class SessionAsyncDispatchTest extends AbstractSessionIT {

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final String EMAIL = "alice@acme.example";

    @Autowired
    private WebSessionStore store;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void seedTheAccount() {
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        SignInTestFixtures.seedAccount(TwoTenantDatabases.tenantA(), EMAIL, "Alice");
    }

    @Test
    @DisplayName("AC-001.16 a session-authenticated request dispatched asynchronously still sees the user")
    void theUserSurvivesTheAsyncDispatch_AC_001_16() throws Exception {
        UUID accountId = TwoTenantDatabases.tenantA()
                .queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, EMAIL);
        String cookie = store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                ACME.id(),
                accountId,
                null,
                ACME.siteHost(),
                "id-token",
                clock.instant(),
                clock.instant()));

        MvcResult started = mvc.perform(
                        get(url(ACME.siteHost(), "/api/v1/test/async")).cookie(new Cookie(SESSION_COOKIE, cookie)))
                .andExpect(request().asyncStarted())
                .andReturn();
        MockHttpServletResponse response =
                mvc.perform(asyncDispatch(started)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains(EMAIL);
    }
}
