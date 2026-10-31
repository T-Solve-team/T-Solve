package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
