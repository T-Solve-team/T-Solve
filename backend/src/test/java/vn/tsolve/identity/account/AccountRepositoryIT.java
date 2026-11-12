package vn.tsolve.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.TenantScope;

/** What only PostgreSQL can show for app_user: the case-insensitive lookup, the unique email, the database split. */
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
class AccountRepositoryIT {

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TenantScope scope;

    @Autowired
    private AccountSignInService signInService;

    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void clean() {
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
    }

    private Account account(String email) {
        return new Account(email, "Lan Tran", AccountStatus.ACTIVE, Instant.now());
    }

    @Test
    @DisplayName("AC-001.12 sign-in finds a stored lower-case email whatever the case asked")
    void findsAStoredLowerCaseEmailWhateverTheCaseAsked_AC_001_12() {
        scope.runIn(TwoTenantDatabases.ACME, () -> accounts.save(account("Lan.Tran@ACME.Example")));

        Optional<Account> found =
                scope.runIn(TwoTenantDatabases.ACME, () -> accounts.findByEmail("lan.tran@acme.example"));
        AccountSignInService.Result result = scope.runIn(
                TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "LAN.tran@acme.EXAMPLE", "Lan Tran"));

        assertThat(found).isPresent();
        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        String stored = TwoTenantDatabases.tenantA().queryForObject("SELECT email FROM app_user", String.class);
        assertThat(stored).isEqualTo("lan.tran@acme.example");
    }

    @Test
    @DisplayName("AC-001.12 the email is unique inside one database")
    void theEmailIsUniqueInsideOneDatabase_AC_001_12() {
        scope.runIn(TwoTenantDatabases.ACME, () -> accounts.save(account("lan@acme.example")));

        assertThatThrownBy(() -> scope.runIn(TwoTenantDatabases.ACME, () -> accounts.save(account("LAN@acme.example"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        // the same email in the other company's database is another row (uniqueness is per database, HLA Q4)
        scope.runIn(TwoTenantDatabases.BETA, () -> accounts.save(account("lan@acme.example")));
        assertThat(TwoTenantDatabases.tenantA().queryForObject("SELECT count(*) FROM app_user", Integer.class))
                .isEqualTo(1);
        assertThat(TwoTenantDatabases.tenantB().queryForObject("SELECT count(*) FROM app_user", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "AC-001.12 the table has the Database Tables PDF columns: id (uuid), full_name VARCHAR(100), department_id; user_role exists")
    void theTableHasThePdfColumns_AC_001_12() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();

        List<String> columns = db.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'app_user'", String.class);
        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "id",
                        "email",
                        "full_name",
                        "status",
                        "first_sign_in_at",
                        "last_sign_in_at",
                        "created_at",
                        "department_id",
                        "keycloak_sub");
        assertThat(db.queryForObject(
                        "SELECT data_type FROM information_schema.columns"
                                + " WHERE table_name = 'app_user' AND column_name = 'id'",
                        String.class))
                .isEqualTo("uuid");
        assertThat(db.queryForObject(
                        "SELECT character_maximum_length FROM information_schema.columns"
                                + " WHERE table_name = 'app_user' AND column_name = 'full_name'",
                        Integer.class))
                .isEqualTo(100);
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'user_role'", Integer.class))
                .as("user_role added by changeset 019 (KAN-107), reused by Task 26b")
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "AC-001.12 keycloak_sub is a nullable VARCHAR(64) with a unique index: sub-less Accounts coexist, one sub twice is refused, the same sub in the other database is accepted")
    void keycloakSubIsNullableAndUnique_AC_001_12() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        JdbcTemplate b = TwoTenantDatabases.tenantB();

        assertThat(a.queryForObject(
                        "SELECT data_type FROM information_schema.columns"
                                + " WHERE table_name = 'app_user' AND column_name = 'keycloak_sub'",
                        String.class))
                .isEqualTo("character varying");
        assertThat(a.queryForObject(
                        "SELECT character_maximum_length FROM information_schema.columns"
                                + " WHERE table_name = 'app_user' AND column_name = 'keycloak_sub'",
                        Integer.class))
                .isEqualTo(64);
        assertThat(a.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns"
                                + " WHERE table_name = 'app_user' AND column_name = 'keycloak_sub'",
                        String.class))
                .isEqualTo("YES");

        a.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('one@acme.example', 'One', 'ACTIVE', now()), ('two@acme.example', 'Two', 'ACTIVE', now())");
        assertThat(a.queryForObject("SELECT count(*) FROM app_user WHERE keycloak_sub IS NULL", Integer.class))
                .isEqualTo(2);

        a.update("INSERT INTO app_user (email, full_name, status, created_at, keycloak_sub)"
                + " VALUES ('three@acme.example', 'Three', 'ACTIVE', now(), 'kc-sub-1')");
        assertThatThrownBy(() -> a.update("INSERT INTO app_user (email, full_name, status, created_at, keycloak_sub)"
                        + " VALUES ('four@acme.example', 'Four', 'ACTIVE', now(), 'kc-sub-1')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_app_user_keycloak_sub");

        b.update("INSERT INTO app_user (email, full_name, status, created_at, keycloak_sub)"
                + " VALUES ('three@acme.example', 'Three', 'ACTIVE', now(), 'kc-sub-1')");
        assertThat(b.queryForObject("SELECT count(*) FROM app_user WHERE keycloak_sub = 'kc-sub-1'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "AC-001.12 / edge 119 in a real database the first sign-in writes the sub, name and times, and a later sign-in by sub with another email leaves app_user.email unchanged")
    void theFirstSignInWritesTheSubAndALaterOneKeepsTheEmail_AC_001_12_edge119() {
        scope.runIn(TwoTenantDatabases.ACME, () -> accounts.save(account("lan@acme.example")));
        TwoTenantDatabases.tenantA().update("UPDATE app_user SET full_name = 'Old Name'");
        JdbcTemplate db = TwoTenantDatabases.tenantA();

        AccountSignInService.Result first = scope.runIn(
                TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "Lan@ACME.example", "Lan Tran"));

        assertThat(first.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user", String.class))
                .isEqualTo("kc-sub-1");
        assertThat(db.queryForObject("SELECT full_name FROM app_user", String.class))
                .isEqualTo("Lan Tran");
        Instant firstAt = db.queryForObject("SELECT first_sign_in_at FROM app_user", Instant.class);
        Instant lastAt = db.queryForObject("SELECT last_sign_in_at FROM app_user", Instant.class);
        assertThat(firstAt).isNotNull();
        assertThat(lastAt).isNotNull();

        AccountSignInService.Result later = scope.runIn(
                TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "lan.new@acme.example", "Lan Tran"));

        assertThat(later.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(later.accountId()).isEqualTo(first.accountId());
        assertThat(db.queryForObject("SELECT email FROM app_user", String.class))
                .isEqualTo("lan@acme.example");
        assertThat(db.queryForObject("SELECT first_sign_in_at FROM app_user", Instant.class))
                .isEqualTo(firstAt);
        assertThat(db.queryForObject("SELECT last_sign_in_at FROM app_user", Instant.class))
                .isAfterOrEqualTo(lastAt);
    }

    @Test
    @DisplayName("AC-001.16 a sign-in with a null name claim keeps the stored full name, against real PostgreSQL")
    void aSignInWithANullNameKeepsTheStoredFullName_AC_001_16() {
        scope.runIn(TwoTenantDatabases.ACME, () -> accounts.save(account("lan@acme.example")));
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("UPDATE app_user SET full_name = 'Stored Name'");

        // first sign-in path (by email, binds the sub)
        AccountSignInService.Result first =
                scope.runIn(TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "lan@acme.example", null));

        assertThat(first.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(db.queryForObject("SELECT full_name FROM app_user", String.class))
                .isEqualTo("Stored Name");
        assertThat(db.queryForObject("SELECT last_sign_in_at FROM app_user", Instant.class))
                .isNotNull();

        // by-sub path
        db.update("UPDATE app_user SET last_sign_in_at = NULL");
        AccountSignInService.Result later = scope.runIn(
                TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "other@acme.example", null));

        assertThat(later.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(db.queryForObject("SELECT full_name FROM app_user", String.class))
                .isEqualTo("Stored Name");
        assertThat(db.queryForObject("SELECT last_sign_in_at FROM app_user", Instant.class))
                .isNotNull();
    }

    @Test
    @DisplayName("edge 131 the conditional bind changes no row when the Account already holds a sub")
    void theConditionalBindChangesNoRowWhenASubIsHeld_edge131() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("INSERT INTO app_user (email, full_name, status, created_at, keycloak_sub)"
                + " VALUES ('held@acme.example', 'Held', 'ACTIVE', now(), 'kc-sub-1')");
        db.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('free@acme.example', 'Free', 'ACTIVE', now())");
        UUID held = db.queryForObject("SELECT id FROM app_user WHERE email = 'held@acme.example'", UUID.class);
        UUID free = db.queryForObject("SELECT id FROM app_user WHERE email = 'free@acme.example'", UUID.class);

        int onHeld = scope.runIn(TwoTenantDatabases.ACME, () -> new TransactionTemplate(transactions)
                .execute(s -> accounts.bindKeycloakSub(held, "kc-sub-2")));
        int onFree = scope.runIn(TwoTenantDatabases.ACME, () -> new TransactionTemplate(transactions)
                .execute(s -> accounts.bindKeycloakSub(free, "kc-sub-2")));

        assertThat(onHeld).isZero();
        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user WHERE id = ?", String.class, held))
                .isEqualTo("kc-sub-1");
        assertThat(onFree).isEqualTo(1);
        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user WHERE id = ?", String.class, free))
                .isEqualTo("kc-sub-2");
    }

    @Test
    @DisplayName(
            "AC-001.56 the conditional bind with the SAME sub the row already holds changes one row and keeps the sub; a different sub changes none")
    void theConditionalBindAcceptsTheSameSub_AC_001_56() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("INSERT INTO app_user (email, full_name, status, created_at, keycloak_sub)"
                + " VALUES ('held@acme.example', 'Held', 'ACTIVE', now(), 'kc-sub-1')");
        UUID held = db.queryForObject("SELECT id FROM app_user", UUID.class);

        int same = scope.runIn(TwoTenantDatabases.ACME, () -> new TransactionTemplate(transactions)
                .execute(s -> accounts.bindKeycloakSub(held, "kc-sub-1")));
        assertThat(same).isEqualTo(1);
        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user WHERE id = ?", String.class, held))
                .isEqualTo("kc-sub-1");

        int other = scope.runIn(TwoTenantDatabases.ACME, () -> new TransactionTemplate(transactions)
                .execute(s -> accounts.bindKeycloakSub(held, "kc-sub-2")));
        assertThat(other).isZero();
        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user WHERE id = ?", String.class, held))
                .isEqualTo("kc-sub-1");
    }

    @Test
    @DisplayName("AC-001.13 the conditional bind changes no row on a Deactivated Account and leaves it unchanged")
    void theConditionalBindSkipsADeactivatedAccount_AC_001_13() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('off@acme.example', 'Off', 'DEACTIVATED', now())");
        UUID off = db.queryForObject("SELECT id FROM app_user", UUID.class);

        int rows = scope.runIn(TwoTenantDatabases.ACME, () -> new TransactionTemplate(transactions)
                .execute(s -> accounts.bindKeycloakSub(off, "kc-sub-1")));

        assertThat(rows).isZero();
        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user WHERE id = ?", String.class, off))
                .isNull();
        assertThat(db.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, off))
                .isEqualTo("DEACTIVATED");
    }

    @Test
    @DisplayName("M2 saving a stale Account never rewrites keycloak_sub: a sub bound meanwhile survives")
    void savingAStaleAccountKeepsTheSubBoundMeanwhile_M2() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('lan@acme.example', 'Lan', 'ACTIVE', now())");
        Account stale = scope.runIn(TwoTenantDatabases.ACME, () -> accounts.findByEmail("lan@acme.example")
                .orElseThrow());
        db.update("UPDATE app_user SET keycloak_sub = 'kc-sub-9'");

        stale.rename("Lan Renamed");
        scope.runIn(TwoTenantDatabases.ACME, () -> accounts.save(stale));

        assertThat(db.queryForObject("SELECT keycloak_sub FROM app_user", String.class))
                .isEqualTo("kc-sub-9");
        assertThat(db.queryForObject("SELECT full_name FROM app_user", String.class))
                .isEqualTo("Lan Renamed");
    }

    @Test
    @DisplayName("AC-001.12 the database refuses an email that is not lower-case")
    void theDatabaseRefusesAnEmailThatIsNotLowerCase_AC_001_12() {
        assertThatThrownBy(() -> TwoTenantDatabases.tenantA()
                        .update("INSERT INTO app_user (email, full_name, status, created_at)"
                                + " VALUES ('Lan@Acme.Example', 'Lan', 'ACTIVE', now())"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_user_email_lower");
    }

    @Test
    @DisplayName("AC-001.12 / edge 25 the sign-in lookup of a mixed-case email uses the unique email index")
    void theSignInLookupUsesTheUniqueEmailIndex_AC_001_12_edge25() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " SELECT 'user' || n || '@acme.example', 'User ' || n, 'ACTIVE', now()"
                + " FROM generate_series(1, 5000) n");
        db.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('lan.tran@acme.example', 'Lan Tran', 'ACTIVE', now())");
        db.execute("ANALYZE app_user");
        Thread.sleep(1500); // statistics reach pg_stat_* about a second after the statement
        long before = emailIndexScans(db);

        AccountSignInService.Result result = scope.runIn(
                TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "Lan.TRAN@Acme.Example", "Lan Tran"));

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        long after = before;
        for (int i = 0; i < 40 && after == before; i++) {
            Thread.sleep(250);
            after = emailIndexScans(db);
        }
        assertThat(after)
                .as("scans of the unique email index (a lookup on upper(email) cannot use it)")
                .isGreaterThan(before);
    }

    private static long emailIndexScans(JdbcTemplate db) {
        return db.queryForObject(
                "SELECT coalesce(sum(s.idx_scan), 0) FROM pg_stat_user_indexes s"
                        + " JOIN pg_index i ON i.indexrelid = s.indexrelid"
                        + " WHERE s.relname = 'app_user' AND s.indexrelname = 'uq_app_user_email'",
                Long.class);
    }

    @Test
    @DisplayName("AC-001.15 / edge 24 an Account of database B is not found while Workspace A is bound")
    void anAccountOfDatabaseBIsNotFoundWhileWorkspaceAIsBound_AC_001_15_edge24() {
        scope.runIn(TwoTenantDatabases.BETA, () -> accounts.save(account("lan.tran@acme.example")));

        Optional<Account> inA =
                scope.runIn(TwoTenantDatabases.ACME, () -> accounts.findByEmail("lan.tran@acme.example"));
        Optional<Account> inB =
                scope.runIn(TwoTenantDatabases.BETA, () -> accounts.findByEmail("lan.tran@acme.example"));

        assertThat(inA).isEmpty();
        assertThat(inB).isPresent();
        assertThat(TwoTenantDatabases.tenantA().queryForObject("SELECT count(*) FROM app_user", Integer.class))
                .isZero();
        assertThat(TwoTenantDatabases.tenantB().queryForObject("SELECT count(*) FROM app_user", Integer.class))
                .isEqualTo(1);
    }
}
