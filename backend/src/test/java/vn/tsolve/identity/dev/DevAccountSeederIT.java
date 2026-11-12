package vn.tsolve.identity.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.dev.DevAccounts.DevAccount;
import vn.tsolve.identity.session.SessionTestFixtures;
import vn.tsolve.identity.signin.SignInTestFixtures;

/**
 * AC-001.26: in the dev profile the seeder adds each {@link DevAccounts} Account to its own Workspace's database with
 * its Roles (Q1 = the Roles are a field of {@code DevAccount}), and never removes or overwrites anything (Q2 = A).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TwoTenantDatabases.class)
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class DevAccountSeederIT {

    @Autowired
    private DevAccountSeeder seeder;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        SessionTestFixtures.registerRedis(registry);
    }

    /**
     * The Roles the seeder must give this dev Account: the {@code roles()} of {@code DevAccount} (Q1 = B). Read by
     * reflection so this test compiles before the developer adds the field; until then it fails here.
     */
    @SuppressWarnings("unchecked")
    static Set<String> seededRoles(DevAccount account) {
        try {
            Object roles = DevAccount.class.getMethod("roles").invoke(account);
            Set<String> names = new TreeSet<>();
            for (Object role : (Iterable<Object>) roles) {
                names.add(role.toString());
            }
            return names;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("DevAccount has no roles() accessor (Q1 = B): " + e, e);
        }
    }

    private static JdbcTemplate dbOf(DevAccount account) {
        return account.workspaceId().equals(DevAccounts.ACME_ID)
                ? TwoTenantDatabases.tenantA()
                : TwoTenantDatabases.tenantB();
    }

    private static JdbcTemplate otherDbOf(DevAccount account) {
        return account.workspaceId().equals(DevAccounts.ACME_ID)
                ? TwoTenantDatabases.tenantB()
                : TwoTenantDatabases.tenantA();
    }

    private static List<String> rolesInDb(JdbcTemplate db, String email) {
        return db.queryForList(
                "SELECT r.role FROM user_role r JOIN app_user u ON u.id = r.user_id WHERE u.email = ? ORDER BY r.role",
                String.class,
                email);
    }

    private static int count(JdbcTemplate db, String table) {
        return db.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    @DisplayName("AC-001.26 the dev seeder creates each DevAccounts Account in its own Workspace with its Roles, and a"
            + " second run changes nothing")
    void seedsEachDevAccountWithItsRolesAndIsIdempotent_AC_001_26() throws Exception {
        assertThat(DevAccounts.all().stream()
                        .map(DevAccountSeederIT::seededRoles)
                        .filter(r -> !r.isEmpty()))
                .as("at least one dev Account has a Role (Q1 = B)")
                .isNotEmpty();
        for (DevAccount account : DevAccounts.all()) {
            JdbcTemplate own = dbOf(account);
            assertThat(own.queryForObject("SELECT status FROM app_user WHERE email = ?", String.class, account.email()))
                    .as("%s in its own database", account.email())
                    .isEqualTo("ACTIVE");
            assertThat(otherDbOf(account)
                            .queryForObject(
                                    "SELECT count(*) FROM app_user WHERE email = ?", Integer.class, account.email()))
                    .as("%s in the other database", account.email())
                    .isZero();
            assertThat(rolesInDb(own, account.email())).containsExactlyElementsOf(seededRoles(account));
        }

        Map<String, Object> idsBefore = Map.of(
                "a", TwoTenantDatabases.tenantA().queryForList("SELECT id FROM app_user ORDER BY email"),
                "b", TwoTenantDatabases.tenantB().queryForList("SELECT id FROM app_user ORDER BY email"));
        int usersA = count(TwoTenantDatabases.tenantA(), "app_user");
        int usersB = count(TwoTenantDatabases.tenantB(), "app_user");
        int rolesA = count(TwoTenantDatabases.tenantA(), "user_role");
        int rolesB = count(TwoTenantDatabases.tenantB(), "user_role");

        seeder.run(null);

        assertThat(count(TwoTenantDatabases.tenantA(), "app_user")).isEqualTo(usersA);
        assertThat(count(TwoTenantDatabases.tenantB(), "app_user")).isEqualTo(usersB);
        assertThat(count(TwoTenantDatabases.tenantA(), "user_role")).isEqualTo(rolesA);
        assertThat(count(TwoTenantDatabases.tenantB(), "user_role")).isEqualTo(rolesB);
        assertThat(TwoTenantDatabases.tenantA().queryForList("SELECT id FROM app_user ORDER BY email"))
                .isEqualTo(idsBefore.get("a"));
        assertThat(TwoTenantDatabases.tenantB().queryForList("SELECT id FROM app_user ORDER BY email"))
                .isEqualTo(idsBefore.get("b"));

        // Q2 = A: an Account that was already present keeps its id, its sub and a Role added by hand
        DevAccount alice = DevAccounts.all().get(0);
        JdbcTemplate db = dbOf(alice);
        db.update("DELETE FROM app_user WHERE email = ?", alice.email());
        UUID id = UUID.randomUUID();
        db.update(
                "INSERT INTO app_user (id, email, full_name, status, keycloak_sub, created_at)"
                        + " VALUES (?, ?, 'Hand Made', 'ACTIVE', 'kc-existing-sub', now())",
                id,
                alice.email());
        db.update("INSERT INTO user_role (user_id, role, assigned_at) VALUES (?, 'TICKET_RESOLVER', now())", id);

        seeder.run(null);

        assertThat(db.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, alice.email()))
                .isEqualTo(id);
        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user WHERE email = ?", String.class, alice.email()))
                .isEqualTo("kc-existing-sub");
        Set<String> expected = new TreeSet<>(seededRoles(alice));
        expected.add("TICKET_RESOLVER");
        assertThat(rolesInDb(db, alice.email())).containsExactlyElementsOf(expected);
        assertThat(db.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, alice.email()))
                .isEqualTo(1);
    }

    /** The tenant databases are shared by the whole failsafe JVM: remove the dev Accounts this class seeded. */
    @AfterAll
    static void removeSeededDevAccounts() {
        removeDevAccounts();
    }

    static void removeDevAccounts() {
        for (DevAccount account : DevAccounts.all()) {
            dbOf(account).update("DELETE FROM app_user WHERE email = ?", account.email());
        }
    }

    /** BUG-198: alice@acme.test stayed in the shared tenant_a and MockSignedInCallerIT counted 8 instead of 7. */
    @Test
    void bug198_cleanupLeavesNoDevAccountInTenantDatabases() throws Exception {
        seeder.run(null);
        for (DevAccount account : DevAccounts.all()) {
            assertThat(count(dbOf(account), "app_user WHERE email = '" + account.email() + "'"))
                    .as("%s seeded before the cleanup", account.email())
                    .isEqualTo(1);
        }

        removeDevAccounts();

        for (DevAccount account : DevAccounts.all()) {
            for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
                assertThat(count(db, "app_user WHERE email = '" + account.email() + "'"))
                        .as("%s left in a tenant database", account.email())
                        .isZero();
            }
        }
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            assertThat(count(db, "user_role WHERE user_id NOT IN (SELECT id FROM app_user)"))
                    .as("user_role rows left without an Account")
                    .isZero();
        }

        // later tests of the class (and the next run) need the dev Accounts back
        seeder.run(null);
    }
}
