package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.tsolve.identity.session.SessionTestFixtures.EXPIRY_INDEX;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.memberOf;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.tsolve.identity.TwoTenantDatabases;

/**
 * Task 27b: the filter's limit path and its deactivation path publish exactly one {@link SessionEnded}, after the
 * session is gone (AC-002.3), on the whole chain with real Redis.
 */
class SessionEndedEventTest extends AbstractExpiryIT {

    @Autowired
    private SessionExpiryPublisher sweeper;

    private void assertOneEventWithTheIds(String cookie, SessionEnded.Reason reason) {
        List<SessionEndedRecorder.Received> received = SessionEndedRecorder.all();
        assertThat(received).hasSize(1);
        SessionEnded event = received.get(0).event();
        assertThat(event.reason()).isEqualTo(reason);
        assertThat(event.accountId()).isEqualTo(account);
        assertThat(event.workspaceId()).isEqualTo(ACME.id());
        assertThat(event.sessionId()).isEqualTo(sha256Hex(cookie)).isNotEqualTo(cookie);
        assertThat(received.get(0).keyExistedAtPublish()).isFalse();
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(redis.opsForZSet().score(EXPIRY_INDEX, memberOf(cookie, ACME.id(), account)))
                .isNull();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"idle 61 min", "absolute 8 h + 1 s"})
    @DisplayName("AC-002.3 the filter's limit path publishes one EXPIRED with the ids after the session is gone")
    void endsAndPublishesOneExpired_AC_002_3(String row) throws Exception {
        String cookie;
        if (row.startsWith("idle")) {
            cookie = acmeSession();
            clock.advance(Duration.ofMinutes(61));
        } else {
            Instant now = clock.instant();
            cookie = acmeSession(now.minus(Duration.ofHours(8)).minusSeconds(1), now);
            // the store's TTL would clamp to one second; keep the key alive so the filter's own limit check ends it
            redis.expire(keyOf(cookie), Duration.ofHours(1));
        }
        int callsBefore = WhoAmITestController.calls();

        MockHttpServletResponse response = whoami(cookie);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo("MSG07");
        assertThat(WhoAmITestController.calls()).isEqualTo(callsBefore);
        assertOneEventWithTheIds(cookie, SessionEnded.Reason.EXPIRED);

        sweeper.sweep();

        assertThat(SessionEndedRecorder.all()).hasSize(1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"Account deactivated", "Account row gone"})
    @DisplayName("AC-002.3 the deactivation path publishes one DEACTIVATED with the ids after the session is gone")
    void deactivatedAccountPublishesOneDeactivated_AC_002_3(String row) throws Exception {
        String cookie = acmeSession();
        boolean gone = row.endsWith("gone");
        if (gone) {
            TwoTenantDatabases.tenantA().update("DELETE FROM app_user WHERE id = ?", account);
        } else {
            TwoTenantDatabases.tenantA().update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", account);
        }

        MockHttpServletResponse response = whoami(cookie);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(gone ? "MSG07" : "MSG10");
        assertOneEventWithTheIds(cookie, SessionEnded.Reason.DEACTIVATED);

        clock.advance(Duration.ofMinutes(61));
        sweeper.sweep();

        assertThat(SessionEndedRecorder.all()).hasSize(1);
    }
}
