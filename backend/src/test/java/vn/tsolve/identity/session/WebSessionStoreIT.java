package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.tsolve.identity.session.SessionTestFixtures.keyOf;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.identity.session.SessionTestFixtures.MutableClock;

/** The Redis session store: create, find, touch, end and the half-written key. */
class WebSessionStoreIT extends AbstractSessionIT {

    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final String ID_TOKEN = "id-token-hint-value-for-the-store-test";

    @Autowired
    private WebSessionStore store;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    private String create() {
        return store.create(new WebSession(
                WebSession.Kind.WORKSPACE,
                WORKSPACE,
                ACCOUNT,
                null,
                "acme.localhost",
                ID_TOKEN,
                MutableClock.START,
                MutableClock.START));
    }

    @Test
    @DisplayName("AC-001.18 create stores the hash key with the session fields and never the cookie value")
    void createStoresTheHashKeyAndNeverTheCookie_AC_001_18() {
        String cookie = create();

        assertThat(cookie).matches("[A-Za-z0-9_-]{43}");
        String key = keyOf(cookie);
        assertThat(redis.keys("*")).containsExactly(key);
        Map<Object, Object> fields = redis.opsForHash().entries(key);
        assertThat(fields).containsEntry("kind", "WORKSPACE");
        assertThat(fields).containsEntry("workspace_id", WORKSPACE.toString());
        assertThat(fields).containsEntry("account_id", ACCOUNT.toString());
        assertThat(fields).containsEntry("site_host", "acme.localhost");
        assertThat(fields).containsEntry("id_token_hint", ID_TOKEN);
        assertThat(fields.get("created_at")).isNotNull();
        assertThat(fields.get("last_activity_at")).isNotNull();
        assertThat(Set.of(
                        "kind",
                        "workspace_id",
                        "account_id",
                        "operator_email",
                        "site_host",
                        "created_at",
                        "last_activity_at",
                        "id_token_hint"))
                .containsAll(fields.keySet().stream().map(Object::toString).toList());
        assertThat(fields.get("operator_email")).isIn(null, "");
        assertThat(fields.keySet().toString() + fields.values()).doesNotContain(cookie);
        assertThat(key).doesNotContain(cookie);
        assertThat(store.keyOf(cookie)).isEqualTo(key);
    }

    @Test
    @DisplayName("AC-001.18 find returns the ID token hint, and the Account and Workspace ids round-trip")
    void findReturnsTheIdTokenHint_AC_001_18() {
        String cookie = create();

        assertThat(store.find(cookie)).get().satisfies(session -> {
            assertThat(session.idTokenHint()).isEqualTo(ID_TOKEN);
            assertThat(session.accountId()).isEqualTo(ACCOUNT);
            assertThat(session.workspaceId()).isEqualTo(WORKSPACE);
            assertThat(session.kind()).isEqualTo(WebSession.Kind.WORKSPACE);
        });
    }

    @Test
    @DisplayName("edge 5 the TTL at create is at most 60 minutes")
    void theTtlAtCreateIsAtMostSixtyMinutes_edge5() {
        String cookie = create();

        Long seconds = redis.getExpire(keyOf(cookie));

        assertThat(seconds).isNotNull().isPositive();
        assertThat(Duration.ofSeconds(seconds)).isLessThanOrEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("AC-001.24 touch sets last_activity_at and renews the TTL to 60 minutes")
    void touchSetsActivityAndRenewsTheTtl_AC_001_24() {
        String cookie = create();
        String key = keyOf(cookie);
        redis.expire(key, Duration.ofSeconds(100));
        Instant later = MutableClock.START.plus(Duration.ofMinutes(10));

        store.touch(cookie, later);

        assertThat(store.find(cookie)).get().satisfies(session -> {
            assertThat(session.lastActivityAt()).isEqualTo(later);
            assertThat(session.createdAt()).isEqualTo(MutableClock.START);
        });
        Long seconds = redis.getExpire(key);
        assertThat(seconds).isNotNull().isBetween(3590L, 3600L);
    }

    @Test
    @DisplayName("edge 12 touch never sets a TTL past created_at + 8 h")
    void touchNeverPassesEightHours_edge12() {
        String cookie = create();
        Instant lateInLife = MutableClock.START.plus(Duration.ofHours(7)).plus(Duration.ofMinutes(50));

        store.touch(cookie, lateInLife);

        Long seconds = redis.getExpire(keyOf(cookie));
        assertThat(seconds).isNotNull().isPositive().isLessThanOrEqualTo(600L);
    }

    @Test
    @DisplayName("edge 13 touch on a deleted key does not recreate it")
    void touchOnDeletedKey_edge13() {
        String cookie = create();
        redis.delete(keyOf(cookie));

        store.touch(cookie, MutableClock.START.plus(Duration.ofMinutes(1)));

        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(redis.keys("*")).isEmpty();
        assertThat(store.find(cookie)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"created_at", "kind"})
    @DisplayName("edge 14 a key missing created_at or kind is read as no session")
    void halfWrittenKey_edge14(String missing) {
        String cookie = create();
        assertThat(store.find(cookie)).isPresent();
        redis.opsForHash().delete(keyOf(cookie), missing);

        assertThat(store.find(cookie)).isEmpty();
    }

    @Test
    @DisplayName("AC-001.23 end deletes the key, and a second end is a no-op")
    void endDeletesTheKey_AC_001_23() {
        String cookie = create();

        store.end(cookie);
        store.end(cookie);

        assertThat(redis.hasKey(keyOf(cookie))).isFalse();
        assertThat(store.find(cookie)).isEmpty();
    }
}
