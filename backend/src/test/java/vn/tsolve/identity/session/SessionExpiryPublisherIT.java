package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static vn.tsolve.identity.session.SessionTestFixtures.EXPIRY_INDEX;
import static vn.tsolve.identity.session.SessionTestFixtures.SESSION_COOKIE;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;
import static vn.tsolve.identity.session.SessionTestFixtures.memberOf;
import static vn.tsolve.identity.session.SessionTestFixtures.scoreOf;
import static vn.tsolve.identity.session.SessionTestFixtures.sha256Hex;
import static vn.tsolve.identity.session.SessionTestFixtures.url;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.tsolve.identity.session.SessionTestFixtures.MutableClock;
import vn.tsolve.identity.signin.IdentityClock;

/**
 * Task 27b: the expiry index kept by the store and the sweep that ends due sessions with one {@code EXPIRED}
 * (AC-002.3, AC-002.4), against a real Redis and its Lua. The scheduler is off in tests; they call {@code sweep()}.
 */
class SessionExpiryPublisherIT extends AbstractExpiryIT {

    private static final Instant START = MutableClock.START;

    @Autowired
    private SessionExpiryPublisher sweeper;

    @Autowired
    private IdentityClock identityClock;

    @Autowired
    private ApplicationEventPublisher events;

    private String member(String cookie) {
        return memberOf(cookie, ACME.id(), account);
    }

    private Double score(String cookie) {
        return redis.opsForZSet().score(EXPIRY_INDEX, member(cookie));
    }

    private void assertOneExpired(String cookie) {
        List<SessionEndedRecorder.Received> received = SessionEndedRecorder.all();
        assertThat(received).hasSize(1);
        SessionEnded event = received.get(0).event();
        assertThat(event.reason()).isEqualTo(SessionEnded.Reason.EXPIRED);
        assertThat(event.accountId()).isEqualTo(account);
        assertThat(event.workspaceId()).isEqualTo(ACME.id());
        assertThat(event.sessionId()).isEqualTo(sha256Hex(cookie)).isNotEqualTo(cookie);
        assertThat(received.get(0).keyExistedAtPublish()).isFalse();
    }

    @Test
    @DisplayName("AC-002.4 create indexes a Workspace session at its deadline and touch moves it, never past"
            + " created + 8 h")
    void createAndTouchKeepTheIndex_AC_002_4() {
        String cookie = acmeSession();

        assertThat(redis.opsForZSet().zCard(EXPIRY_INDEX)).isEqualTo(1L);
        assertThat(score(cookie)).isEqualTo(scoreOf(START.plus(Duration.ofMinutes(60))));

        store.touch(cookie, START.plus(Duration.ofMinutes(30)));
        assertThat(score(cookie)).isEqualTo(scoreOf(START.plus(Duration.ofMinutes(90))));

        store.touch(cookie, START.plus(Duration.ofHours(7)).plus(Duration.ofMinutes(30)));
        assertThat(score(cookie)).isEqualTo(scoreOf(START.plus(Duration.ofHours(8))));
        assertThat(redis.opsForZSet().zCard(EXPIRY_INDEX)).isEqualTo(1L);
    }

    @Test
    @DisplayName("AC-002.4 an unused session past its idle limit is removed and gets one EXPIRED at the next sweep")
    void sweepPublishesExpiredAfterTheIdleLimit_AC_002_4() throws Exception {
        String cookie = acmeSession();
        clock.advance(Duration.ofMinutes(61));

        sweeper.sweep();

        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(score(cookie)).isNull();
        assertOneExpired(cookie);

        sweeper.sweep();
        assertThat(SessionEndedRecorder.all()).hasSize(1);

        MockHttpServletResponse response = whoami(cookie);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo("MSG07");
        assertThat(SessionEndedRecorder.all()).hasSize(1);
    }

    @Test
    @DisplayName("AC-002.4 a session kept active is still removed with one EXPIRED after the 8-hour limit")
    void sweepPublishesExpiredAfterTheAbsoluteLimit_AC_002_4() throws Exception {
        String cookie = acmeSession();
        for (int minutes = 50; minutes <= 450; minutes += 50) {
            moveClockTo(START.plus(Duration.ofMinutes(minutes)));
            assertThat(whoami(cookie).getStatus()).isEqualTo(200);
        }
        assertThat(SessionEndedRecorder.all()).isEmpty();
        moveClockTo(START.plus(Duration.ofHours(8)).plus(Duration.ofMinutes(1)));

        sweeper.sweep();

        assertOneExpired(cookie);
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(score(cookie)).isNull();
    }

    @Test
    @DisplayName("edge 1 at the deadline or before, the sweep ends nothing")
    void nothingIsSweptBeforeTheDeadline_edge1() throws Exception {
        Instant d1 = START.plus(Duration.ofMinutes(60));
        Instant d2 = START.plus(Duration.ofMinutes(70));
        String first = acmeSession(START, START);
        String second = acmeSession(START.plus(Duration.ofMinutes(10)), START.plus(Duration.ofMinutes(10)));

        moveClockTo(d1.minusSeconds(1));
        sweeper.sweep();
        assertThat(SessionEndedRecorder.all()).isEmpty();

        moveClockTo(d1);
        sweeper.sweep();
        assertThat(SessionEndedRecorder.all()).isEmpty();
        assertThat(redis.hasKey(keyOf(first))).isTrue();
        assertThat(score(first)).isEqualTo(scoreOf(d1));
        // the filter agrees: at exactly the deadline the session still lives (background, so it is not renewed)
        assertThat(backgroundWhoami(first).getStatus()).isEqualTo(200);

        moveClockTo(d1.plusSeconds(1));
        sweeper.sweep();
        assertOneExpired(first);
        assertThat(redis.hasKey(keyOf(second))).isTrue();
        assertThat(score(second)).isEqualTo(scoreOf(d2));
    }

    @Test
    @DisplayName("edge 2 a session whose key Redis already dropped (TTL) still gets one EXPIRED with the ids from the"
            + " index member")
    void keyAlreadyDroppedByRedisStillGivesOneExpired_edge2() {
        String cookie = acmeSession();
        redis.delete(keyOf(cookie));
        clock.advance(Duration.ofMinutes(61));

        sweeper.sweep();

        assertOneExpired(cookie);
        assertThat(score(cookie)).isNull();
        sweeper.sweep();
        assertThat(SessionEndedRecorder.all()).hasSize(1);
    }

    @Test
    @DisplayName("edge 3 a session stored without an index entry ends with one EXPIRED on its next request")
    void sessionWithoutIndexEntryStillEndsWithOneEvent_edge3() throws Exception {
        String cookie = acmeSession();
        redis.opsForZSet().remove(EXPIRY_INDEX, member(cookie));
        clock.advance(Duration.ofMinutes(61));

        MockHttpServletResponse response = whoami(cookie);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo("MSG07");
        assertOneExpired(cookie);
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        sweeper.sweep();
        assertThat(SessionEndedRecorder.all()).hasSize(1);
    }

    @Test
    @DisplayName("edge 4 a touch that arrives after the session ended does not re-add the index member, so no second"
            + " event follows")
    void touchAfterTheEndNeverRevivesTheMember_edge4() throws Exception {
        String cookie = acmeSession();
        MockHttpServletResponse logout = perform(post(url(ACME.siteHost(), "/api/v1/auth/logout"))
                .with(csrf())
                .cookie(new Cookie(SESSION_COOKIE, cookie)));
        assertThat(logout.getStatus()).isEqualTo(200);

        store.touch(cookie, clock.instant());

        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(score(cookie)).isNull();
        clock.advance(Duration.ofMinutes(61));
        sweeper.sweep();
        List<SessionEndedRecorder.Received> received = SessionEndedRecorder.all();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).event().reason()).isEqualTo(SessionEnded.Reason.LOGOUT);
    }

    @Test
    @DisplayName("edge 5 two sweeper instances and the filter racing on the same expired sessions publish exactly one"
            + " event per session")
    void oneEventWhenEndPathsRace_edge5() throws Exception {
        List<String> expired = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            expired.add(acmeSession(START, START));
        }
        Instant liveAt = START.plus(Duration.ofMinutes(30));
        String live = acmeSession(liveAt, liveAt);
        double liveScore = score(live);
        clock.advance(Duration.ofMinutes(61));
        SessionExpiryPublisher second = new SessionExpiryPublisher(store, identityClock, events);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(7);
        List<Future<MockHttpServletResponse>> answers = new ArrayList<>();
        try {
            List<Future<?>> sweeps = new ArrayList<>();
            sweeps.add(pool.submit(() -> {
                go.await();
                sweeper.sweep();
                return null;
            }));
            sweeps.add(pool.submit(() -> {
                go.await();
                second.sweep();
                return null;
            }));
            for (String cookie : expired.subList(0, 5)) {
                answers.add(pool.submit(() -> {
                    go.await();
                    return whoami(cookie);
                }));
            }
            go.countDown();
            for (Future<?> sweep : sweeps) {
                sweep.get();
            }
            for (Future<MockHttpServletResponse> answer : answers) {
                MockHttpServletResponse response = answer.get();
                assertThat(response.getStatus()).isEqualTo(401);
                assertThat(codeOf(response)).isEqualTo("MSG07");
            }
        } finally {
            pool.shutdownNow();
        }

        List<SessionEndedRecorder.Received> received = SessionEndedRecorder.all();
        assertThat(received).hasSize(20);
        assertThat(received.stream().map(r -> r.event().sessionId()).collect(Collectors.toSet()))
                .isEqualTo(expired.stream().map(SessionTestFixtures::sha256Hex).collect(Collectors.toSet()));
        assertThat(received).allSatisfy(r -> {
            assertThat(r.event().reason()).isEqualTo(SessionEnded.Reason.EXPIRED);
            assertThat(r.keyExistedAtPublish()).isFalse();
        });
        for (String cookie : expired) {
            assertThat(redis.hasKey(keyOf(cookie))).isFalse();
            assertThat(score(cookie)).isNull();
        }
        assertThat(redis.hasKey(keyOf(live))).isTrue();
        assertThat(score(live)).isEqualTo(liveScore);
    }

    @Test
    @DisplayName("edge 6 an OPERATOR session gets no index entry and the sweep publishes nothing for it")
    void operatorSessionIsNotIndexed_edge6() {
        String cookie = store.create(
                new WebSession(WebSession.Kind.OPERATOR, null, null, "ops@example.test", null, HINT, START, START));

        assertThat(redis.opsForZSet().zCard(EXPIRY_INDEX)).isZero();
        clock.advance(Duration.ofHours(8).plusMinutes(1));
        sweeper.sweep();

        assertThat(SessionEndedRecorder.all()).isEmpty();
        assertThat(redis.hasKey(keyOf(cookie))).isTrue();
    }

    @Test
    @DisplayName("edge 7 the sweep's claim does nothing when the member's score moved past now since it was read")
    void staleDueMemberIsNotClaimed_edge7() {
        String cookie = acmeSession();
        String member = member(cookie);
        long staleNow = START.plus(Duration.ofMinutes(61)).toEpochMilli();
        store.touch(cookie, START.plus(Duration.ofMinutes(30)));

        assertThat(store.claim(member, staleNow)).isFalse();

        assertThat(redis.hasKey(keyOf(cookie))).isTrue();
        assertThat(score(cookie)).isEqualTo(scoreOf(START.plus(Duration.ofMinutes(90))));
        assertThat(SessionEndedRecorder.all()).isEmpty();
        // the claim itself works once the score is due
        assertThat(store.claim(member, START.plus(Duration.ofMinutes(91)).toEpochMilli()))
                .isTrue();
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(score(cookie)).isNull();
    }

    @Test
    @DisplayName("edge 8 (finding b) members that do not parse are removed with no event and do not block the due"
            + " sessions behind them")
    void malformedMembersDoNotBlockTheSweep_edge8() {
        String cookie = acmeSession();
        // lowest scores, so they come first in the due list
        redis.opsForZSet().add(EXPIRY_INDEX, "no-separator-at-all", 1);
        redis.opsForZSet().add(EXPIRY_INDEX, "abc|not-a-uuid|not-a-uuid", 2);
        clock.advance(Duration.ofMinutes(61));

        sweeper.sweep();

        assertThat(redis.opsForZSet().score(EXPIRY_INDEX, "no-separator-at-all"))
                .isNull();
        assertThat(redis.opsForZSet().score(EXPIRY_INDEX, "abc|not-a-uuid|not-a-uuid"))
                .isNull();
        assertOneExpired(cookie);
        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(score(cookie)).isNull();
    }

    @Test
    @DisplayName("edge 9 (finding c) a Workspace session without an Account id is not indexed")
    void workspaceSessionWithoutAnAccountIdIsNotIndexed_edge9() {
        String cookie = store.create(
                new WebSession(WebSession.Kind.WORKSPACE, ACME.id(), null, null, ACME.siteHost(), HINT, START, START));

        assertThat(redis.hasKey(keyOf(cookie))).isTrue();
        assertThat(redis.opsForZSet().zCard(EXPIRY_INDEX)).isZero();
    }
}
