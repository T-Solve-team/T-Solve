package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** What only PostgreSQL can show for sign_in_states: the one-target-shape check and the atomic single use. */
@SpringBootTest
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class SignInStateRepositoryIT {

    // started once for the class and stopped by Testcontainers' Ryuk; no org.testcontainers:junit-jupiter needed
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    // looked up per test, so a missing repository fails the markUsed tests, not the whole context
    @Autowired
    private ObjectProvider<SignInStateRepository> repositories;

    private void insert(UUID id, String target, UUID workspaceId, Instant createdAt, Instant expiresAt) {
        jdbc.update(
                "INSERT INTO sign_in_states (id, target, workspace_id, tracker_account_id, return_to, nonce,"
                        + " pkce_verifier, created_at, expires_at, used_at)"
                        + " VALUES (?, ?, ?, NULL, '/', ?, ?, ?, ?, NULL)",
                id,
                target,
                workspaceId,
                "nonce-" + id,
                "verifier-" + id,
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    private void insertFresh(UUID id, String target, UUID workspaceId) {
        Instant now = Instant.now();
        insert(id, target, workspaceId, now, now.plus(SignInTestFixtures.STATE_LIFETIME));
    }

    private int markUsed(UUID id, Instant usedAt) {
        SignInStateRepository repository = repositories.getObject();
        return transactions.execute(status -> repository.markUsed(id, usedAt));
    }

    private Instant usedAt(UUID id) {
        OffsetDateTime usedAt =
                jdbc.queryForObject("SELECT used_at FROM sign_in_states WHERE id = ?", OffsetDateTime.class, id);
        return usedAt == null ? null : usedAt.toInstant();
    }

    /** Like {@link #insert} but sets the binding columns of changeset 005 (they do not exist before it). */
    private void insertWithBinding(
            UUID id, Instant createdAt, Instant expiresAt, Instant authorizedAt, byte[] bindingHash, Instant usedAt) {
        jdbc.update(
                "INSERT INTO sign_in_states (id, target, workspace_id, tracker_account_id, return_to, nonce,"
                        + " pkce_verifier, created_at, expires_at, used_at, authorized_at, browser_binding_hash)"
                        + " VALUES (?, 'PLATFORM_CONSOLE', NULL, NULL, '/', ?, ?, ?, ?, ?, ?, ?)",
                id,
                "nonce-" + id,
                "verifier-" + id,
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                usedAt == null ? null : OffsetDateTime.ofInstant(usedAt, ZoneOffset.UTC),
                authorizedAt == null ? null : OffsetDateTime.ofInstant(authorizedAt, ZoneOffset.UTC),
                bindingHash);
    }

    private void insertFreshWithBinding(UUID id) {
        Instant now = Instant.now();
        insertWithBinding(id, now, now.plus(SignInTestFixtures.STATE_LIFETIME), null, null, null);
    }

    private static byte[] hash(int first) {
        byte[] hash = new byte[32];
        hash[0] = (byte) first;
        return hash;
    }

    private int markAuthorized(UUID id, byte[] hash, Instant now) {
        SignInStateRepository repository = repositories.getObject();
        return transactions.execute(status -> repository.markAuthorized(id, hash, now));
    }

    private Instant authorizedAt(UUID id) {
        OffsetDateTime at =
                jdbc.queryForObject("SELECT authorized_at FROM sign_in_states WHERE id = ?", OffsetDateTime.class, id);
        return at == null ? null : at.toInstant();
    }

    private byte[] storedHash(UUID id) {
        return jdbc.queryForObject("SELECT browser_binding_hash FROM sign_in_states WHERE id = ?", byte[].class, id);
    }

    @Test
    @DisplayName("AC-001.44 markAuthorized sets authorized_at and the 32-byte hash once: 1, then 0")
    void markAuthorizedSetsTheColumnsOnce_AC_001_44() {
        UUID id = UUID.randomUUID();
        insertFreshWithBinding(id);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        assertThat(markAuthorized(id, hash(1), now)).isEqualTo(1);
        assertThat(markAuthorized(id, hash(2), now.plusSeconds(1))).isZero();

        assertThat(storedHash(id)).isEqualTo(hash(1));
        assertThat(authorizedAt(id)).isEqualTo(now);
    }

    @Test
    @DisplayName("AC-001.45 markAuthorized returns 0 for a used state")
    void markAuthorizedReturnsZeroForAUsedState_AC_001_45() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        insertWithBinding(id, now, now.plus(SignInTestFixtures.STATE_LIFETIME), null, null, now);

        assertThat(markAuthorized(id, hash(1), now)).isZero();
        assertThat(authorizedAt(id)).isNull();
    }

    @Test
    @DisplayName("AC-001.45 / edge 20 markAuthorized returns 0 when expires_at is not after :now")
    void markAuthorizedReturnsZeroAtExpiry_AC_001_45_edge20() {
        Instant expiresAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID atExpiry = UUID.randomUUID();
        UUID justBefore = UUID.randomUUID();
        insertWithBinding(atExpiry, expiresAt.minusSeconds(600), expiresAt, null, null, null);
        insertWithBinding(justBefore, expiresAt.minusSeconds(600), expiresAt, null, null, null);

        assertThat(markAuthorized(atExpiry, hash(1), expiresAt)).isZero();
        assertThat(markAuthorized(justBefore, hash(1), expiresAt.minusSeconds(1)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Q9 markAuthorized compares expires_at with the passed time, not the database now()")
    void markAuthorizedUsesThePassedTime_Q9() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MICROS);
        insertWithBinding(id, created, created.plus(Duration.ofMinutes(10)), null, null, null);

        assertThat(markAuthorized(id, hash(1), created.plus(Duration.ofMinutes(1))))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-001.2 the check constraint rejects authorized_at without a hash")
    void rejectsAuthorizedAtWithoutAHash_AC_001_2() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        assertThatThrownBy(() -> insertWithBinding(UUID.randomUUID(), now, now.plusSeconds(600), now, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AC-001.2 the check constraint rejects a hash without authorized_at")
    void rejectsAHashWithoutAuthorizedAt_AC_001_2() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        assertThatThrownBy(() -> insertWithBinding(UUID.randomUUID(), now, now.plusSeconds(600), null, hash(1), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AC-001.44 / edge 51 two concurrent markAuthorized calls let exactly one through")
    void twoConcurrentMarkAuthorizedLetExactlyOneThrough_AC_001_44_edge51() throws Exception {
        UUID id = UUID.randomUUID();
        insertFreshWithBinding(id);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 1; i <= 2; i++) {
                byte[] hash = hash(i);
                results.add(pool.submit(() -> {
                    start.await();
                    return markAuthorized(id, hash, now);
                }));
            }
            start.countDown();
            List<Integer> counts = new ArrayList<>();
            for (Future<Integer> result : results) {
                counts.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(counts).containsExactlyInAnyOrder(1, 0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-001.2 the check constraint rejects a Workspace state without a Workspace")
    void rejectsWorkspaceStateWithoutWorkspace_AC_001_2() {
        insertFresh(UUID.randomUUID(), "WORKSPACE", UUID.randomUUID());

        assertThatThrownBy(() -> insertFresh(UUID.randomUUID(), "WORKSPACE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AC-001.2 the check constraint rejects a Platform Console state with a Workspace")
    void rejectsPlatformConsoleStateWithWorkspace_AC_001_2() {
        insertFresh(UUID.randomUUID(), "PLATFORM_CONSOLE", null);

        assertThatThrownBy(() -> insertFresh(UUID.randomUUID(), "PLATFORM_CONSOLE", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AC-001.2 markUsed marks a fresh state used once: 1, then 0")
    void markUsedMarksFreshStateOnce_AC_001_2() {
        UUID id = UUID.randomUUID();
        insertFresh(id, "PLATFORM_CONSOLE", null);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        assertThat(markUsed(id, now)).isEqualTo(1);
        assertThat(markUsed(id, now.plusSeconds(1))).isZero();
        assertThat(usedAt(id)).isEqualTo(now);
    }

    @Test
    @DisplayName("AC-001.2 / edge 20 markUsed after expires_at returns 0")
    void markUsedAfterExpiryReturnsZero_AC_001_2_edge20() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        insert(id, "WORKSPACE", UUID.randomUUID(), now.minus(Duration.ofMinutes(11)), now.minusSeconds(60));

        assertThat(markUsed(id, now)).isZero();
        assertThat(usedAt(id)).isNull();
    }
}
