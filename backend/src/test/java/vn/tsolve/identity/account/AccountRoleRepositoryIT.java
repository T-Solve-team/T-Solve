package vn.tsolve.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.TenantScope;

/** AC-001.26: the Roles of an Account are read from {@code user_role} of the bound Workspace's database only. */
@SpringBootTest
@Import(TwoTenantDatabases.class)
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
class AccountRoleRepositoryIT {

    @Autowired
    private AccountRoleRepository roles;

    @Autowired
    private TenantScope scope;

    @BeforeEach
    void clean() {
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    private static void user(JdbcTemplate db, UUID id, String email) {
        db.update(
                "INSERT INTO app_user (id, email, full_name, status, created_at) VALUES (?, ?, 'X', 'ACTIVE', now())",
                id,
                email);
    }

    private static void role(JdbcTemplate db, UUID user, Role role, Instant assignedAt) {
        db.update(
                "INSERT INTO user_role (user_id, role, assigned_at) VALUES (?, ?, ?)",
                user,
                role.name(),
                Timestamp.from(assignedAt));
    }

    @Test
    @DisplayName("AC-001.26 findByUserId returns exactly that Account's Roles from the bound Workspace's database")
    void findsOnlyThatAccountsRolesInTheBoundWorkspace_AC_001_26() {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        UUID lonely = UUID.randomUUID();
        Instant first = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant second = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant inBeta = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        JdbcTemplate b = TwoTenantDatabases.tenantB();
        user(a, x, "x@acme.example");
        user(a, y, "y@acme.example");
        user(a, lonely, "lonely@acme.example");
        user(b, x, "x@beta.example");
        role(a, x, Role.ADMIN, first);
        role(a, x, Role.TICKET_RESOLVER, second);
        role(a, y, Role.PROJECT_MANAGER, first);
        role(b, x, Role.KNOWLEDGE_MANAGER, inBeta);

        List<AccountRole> inAcme = scope.runIn(TwoTenantDatabases.ACME, () -> roles.findByUserId(x));
        List<AccountRole> inBetaRoles = scope.runIn(TwoTenantDatabases.BETA, () -> roles.findByUserId(x));
        List<AccountRole> none = scope.runIn(TwoTenantDatabases.ACME, () -> roles.findByUserId(lonely));

        assertThat(inAcme)
                .extracting(AccountRole::userId, AccountRole::role, AccountRole::assignedAt)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(x, Role.ADMIN, first),
                        org.assertj.core.groups.Tuple.tuple(x, Role.TICKET_RESOLVER, second));
        assertThat(inBetaRoles)
                .extracting(AccountRole::userId, AccountRole::role, AccountRole::assignedAt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(x, Role.KNOWLEDGE_MANAGER, inBeta));
        assertThat(none).isEmpty();
    }
}
