package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.callbackRequest;
import static vn.tsolve.identity.session.SessionTestFixtures.claimsOf;
import static vn.tsolve.identity.session.SessionTestFixtures.cookieValue;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.session.AbstractSessionIT;
import vn.tsolve.identity.session.SessionEnded;
import vn.tsolve.identity.session.SessionEndedRecorder;
import vn.tsolve.identity.session.SessionExpiryPublisher;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.session.WebSession;
import vn.tsolve.identity.session.WebSessionStore;
import vn.tsolve.identity.session.WhoAmITestController;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

/**
 * Task 27c (BUG-203): the callback that ends the old Workspace session of the same browser (AC-001.18) publishes one
 * {@code SessionEnded} {@code LOGOUT} for it (AC-002.3), except for a session already past a limit (AC-002.4) and an
 * Operator session. Same set-up as {@link SessionCreationTest}.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import({WhoAmITestController.class, SessionEndedRecorder.class, SessionReplacedEventTest.ThrowingLogoutListener.class})
class SessionReplacedEventTest extends AbstractSessionIT {

    /** Test-only LOGOUT listener that throws while armed, to prove a failing cleanup does not break the sign-in. */
    @TestComponent
    static class ThrowingLogoutListener {
        static volatile boolean armed;
        static final AtomicInteger CALLS = new AtomicInteger();

        @EventListener
        void on(SessionEnded event) {
            if (armed && event.reason() == SessionEnded.Reason.LOGOUT) {
                CALLS.incrementAndGet();
                throw new IllegalStateException("secret-text");
            }
        }
    }

    private static final WorkspaceRef ACME = InMemoryWorkspaceLookup.ACME;
    private static final WorkspaceRef BETA = InMemoryWorkspaceLookup.BETA;
    private static final String ACME_HOST = ACME.siteHost();
    private static final String EMAIL = "alice@acme.example";
    private static final String RETURN_TO = "/tickets/42?tab=history";
    private static final Instant START = SessionTestFixtures.MutableClock.START;

    @Autowired
    private OidcCallbackHandler handler;

    @Autowired
    private WebSessionStore store;

    @Autowired
    private SessionExpiryPublisher sweeper;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    @BeforeEach
    void seedTheAccount() {
        WhoAmITestController.reset();
        SessionEndedRecorder.reset();
        ThrowingLogoutListener.armed = false;
        ThrowingLogoutListener.CALLS.set(0);
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        SignInTestFixtures.seedAccount(TwoTenantDatabases.tenantA(), EMAIL, "Alice");
    }

    // ---- helpers

    private UUID newAccountId() {
        return TwoTenantDatabases.tenantA()
                .queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, EMAIL);
    }

    private String oldSession(WorkspaceRef workspace, UUID oldAccount) {
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                workspace.id(),
                oldAccount,
                null,
                workspace.siteHost(),
                "old-id-token",
                clock.instant(),
                clock.instant()));
    }

    private MockHttpServletResponse callback(String returnTo, String cookie) throws Exception {
        MockHttpServletRequest request = callbackRequest(ACME_HOST);
        if (returnTo != null) {
            request.setAttribute(OidcCallbackHandler.RETURN_TO_ATTRIBUTE, returnTo);
        }
        if (cookie != null) {
            request.setCookies(new Cookie(SESSION_COOKIE, cookie));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, SignInTestFixtures.signedIn(claimsOf(ACME, EMAIL)));
        return response;
    }

    private void moveClockTo(Instant target) {
        clock.advance(Duration.between(clock.instant(), target));
    }

    private static URI locationOf(MockHttpServletResponse response) {
        return URI.create(url(ACME_HOST, "/api/v1/auth/callback")).resolve(response.getHeader(HttpHeaders.LOCATION));
    }

    private void assertOneLogoutFor(WorkspaceRef workspace, UUID oldAccount, String oldCookie) {
        List<SessionEndedRecorder.Received> received = SessionEndedRecorder.all();
        assertThat(received).hasSize(1);
        SessionEnded event = received.get(0).event();
        assertThat(event.reason()).isEqualTo(SessionEnded.Reason.LOGOUT);
        assertThat(event.workspaceId()).isEqualTo(workspace.id());
        assertThat(event.accountId()).isEqualTo(oldAccount);
        assertThat(event.sessionId()).isEqualTo(sha256Hex(oldCookie)).isNotEqualTo(oldCookie);
        assertThat(received.get(0).keyExistedAtPublish()).isFalse();
    }

    // ---- tests

    @Test
    @DisplayName("AC-002.3 a session replaced by a new sign-in publishes one LOGOUT with its own Workspace and Account"
            + " ids, after its key is gone")
    void replacedWorkspaceSessionPublishesLogoutWithItsOwnIds_BUG_203_AC_002_3() throws Exception {
        UUID oldAccount = UUID.randomUUID();
        String oldCookie = oldSession(BETA, oldAccount);

        MockHttpServletResponse response = callback(null, oldCookie);

        String fresh = cookieValue(response, SESSION_COOKIE);
        assertThat(fresh).isNotNull().isNotEqualTo(oldCookie);
        assertThat(oldAccount).isNotEqualTo(newAccountId());
        assertOneLogoutFor(BETA, oldAccount, oldCookie);
        assertThat(redis.hasKey(keyOf(oldCookie))).isFalse();
        assertThat(redis.hasKey(keyOf(fresh))).isTrue();
    }

    @Test
    @DisplayName("AC-002.3 an Operator session replaced by a new sign-in publishes nothing")
    void operatorSessionReplacedPublishesNothing_AC_002_3() throws Exception {
        String oldCookie = store.create(new WebSession(
                WebSession.Kind.OPERATOR,
                null,
                null,
                "ops@example.test",
                null,
                "operator-id-token",
                clock.instant(),
                clock.instant()));

        MockHttpServletResponse response = callback(null, oldCookie);

        String fresh = cookieValue(response, SESSION_COOKIE);
        assertThat(fresh).isNotNull();
        assertThat(redis.hasKey(keyOf(oldCookie))).isFalse();
        assertThat(redis.hasKey(keyOf(fresh))).isTrue();
        assertThat(SessionEndedRecorder.all()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"idleMoreThan60Minutes", "olderThan8Hours"})
    @DisplayName("AC-002.4 an old session already past a limit gets no LOGOUT from the callback; the key is deleted"
            + " and the sweep still publishes its EXPIRED")
    void pastLimitOldSessionIsLeftToTheSweepAndStaysExpired_AC_002_4(String limit) throws Exception {
        UUID oldAccount = UUID.randomUUID();
        String oldCookie = oldSession(BETA, oldAccount);
        if ("idleMoreThan60Minutes".equals(limit)) {
            clock.advance(Duration.ofMinutes(61));
        } else {
            // kept active, so only the 8-hour limit is exceeded
            for (int minutes = 50; minutes <= 450; minutes += 50) {
                moveClockTo(START.plus(Duration.ofMinutes(minutes)));
                store.touch(oldCookie, clock.instant());
            }
            moveClockTo(START.plus(Duration.ofHours(8)).plus(Duration.ofMinutes(1)));
        }

        MockHttpServletResponse response = callback(RETURN_TO, oldCookie);

        assertThat(response.getStatus()).isEqualTo(302);
        String fresh = cookieValue(response, SESSION_COOKIE);
        assertThat(fresh).isNotNull();
        assertThat(redis.hasKey(keyOf(fresh))).isTrue();
        assertThat(SessionEndedRecorder.all()).as("no LOGOUT from the callback").isEmpty();
        assertThat(redis.hasKey(keyOf(oldCookie))).as("the old key is deleted").isFalse();

        sweeper.sweep();

        List<SessionEndedRecorder.Received> received = SessionEndedRecorder.all();
        assertThat(received).hasSize(1);
        SessionEnded event = received.get(0).event();
        assertThat(event.reason()).isEqualTo(SessionEnded.Reason.EXPIRED);
        assertThat(event.workspaceId()).isEqualTo(BETA.id());
        assertThat(event.accountId()).isEqualTo(oldAccount);
        assertThat(event.sessionId()).isEqualTo(sha256Hex(oldCookie));
        assertThat(redis.hasKey(keyOf(fresh)))
                .as("the new session is not swept")
                .isTrue();
    }

    @Test
    @DisplayName("edge 142 an old session idle exactly 60 minutes still counts as live: one LOGOUT")
    void exactlyAtTheLimitStillCountsAsLive_edge142a() throws Exception {
        UUID oldAccount = UUID.randomUUID();
        String oldCookie = oldSession(BETA, oldAccount);
        clock.advance(Duration.ofMinutes(60));

        callback(null, oldCookie);

        assertOneLogoutFor(BETA, oldAccount, oldCookie);
        assertThat(redis.hasKey(keyOf(oldCookie))).isFalse();
    }

    @Test
    @DisplayName("edge 142 two sign-ins with the same old cookie, in turn or at once, publish one LOGOUT")
    void twoSignInsWithTheSameOldCookiePublishOneLogout_edge142b() throws Exception {
        UUID oldAccount = UUID.randomUUID();
        String oldCookie = oldSession(BETA, oldAccount);

        callback(null, oldCookie);
        callback(null, oldCookie);

        assertOneLogoutFor(BETA, oldAccount, oldCookie);

        // the same, started together on two threads
        SessionEndedRecorder.reset();
        UUID secondAccount = UUID.randomUUID();
        String secondCookie = oldSession(BETA, secondAccount);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MockHttpServletResponse>> answers = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                answers.add(pool.submit(() -> {
                    go.await();
                    return callback(null, secondCookie);
                }));
            }
            go.countDown();
            for (Future<MockHttpServletResponse> answer : answers) {
                assertThat(answer.get().getStatus()).isEqualTo(302);
            }
        } finally {
            pool.shutdownNow();
        }

        assertOneLogoutFor(BETA, secondAccount, secondCookie);
    }

    @Test
    @DisplayName("AC-001.18 the answer and the cookies on the replaced-session path are the same as without an old"
            + " session")
    void replacedSessionPathKeepsTheAnswerAndCookies_AC_001_18() throws Exception {
        MockHttpServletResponse plain = callback(RETURN_TO, null);
        String oldCookie = oldSession(BETA, UUID.randomUUID());

        MockHttpServletResponse replaced = callback(RETURN_TO, oldCookie);

        assertThat(replaced.getStatus()).isEqualTo(302).isEqualTo(plain.getStatus());
        assertThat(locationOf(replaced)).isEqualTo(locationOf(plain)).isEqualTo(URI.create(url(ACME_HOST, RETURN_TO)));
        List<String> sessionCookies = SignInTestFixtures.setCookie(replaced, SESSION_COOKIE);
        assertThat(sessionCookies).hasSize(1);
        Map<String, String> attributes = SignInTestFixtures.attributes(sessionCookies.get(0));
        assertThat(attributes)
                .isEqualTo(SignInTestFixtures.attributes(
                        SignInTestFixtures.setCookie(plain, SESSION_COOKIE).get(0)));
        assertThat(attributes).containsEntry("path", "/").containsKeys("secure", "httponly");
        assertThat(attributes.get("samesite")).isEqualToIgnoringCase("Lax");
        assertThat(attributes).doesNotContainKeys("domain", "max-age", "expires");
        List<String> authRequest =
                SignInTestFixtures.setCookie(replaced, RedisAuthorizationRequestRepository.COOKIE_NAME);
        assertThat(authRequest).hasSize(1);
        assertThat(SignInTestFixtures.attributes(authRequest.get(0))).containsEntry("max-age", "0");
        assertThat(replaced.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(replaced.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2);
        assertThat(replaced.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("edge 142 a LOGOUT listener that throws does not break the sign-in and its text is not logged")
    void aThrowingLogoutListenerDoesNotBreakTheSignIn_edge142c(CapturedOutput output) throws Exception {
        UUID oldAccount = UUID.randomUUID();
        String oldCookie = oldSession(BETA, oldAccount);
        ThrowingLogoutListener.armed = true;

        MockHttpServletResponse response = callback(RETURN_TO, oldCookie);

        assertThat(ThrowingLogoutListener.CALLS.get()).as("the listener ran").isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(locationOf(response)).isEqualTo(URI.create(url(ACME_HOST, RETURN_TO)));
        String fresh = cookieValue(response, SESSION_COOKIE);
        assertThat(fresh).isNotNull();
        assertThat(redis.hasKey(keyOf(fresh))).isTrue();
        assertThat(redis.hasKey(keyOf(oldCookie))).isFalse();
        assertThat(output.getAll()).contains("IllegalStateException").doesNotContain("secret-text");
    }
}
