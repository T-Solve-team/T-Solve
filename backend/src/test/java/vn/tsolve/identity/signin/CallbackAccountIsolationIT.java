package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.account.AccountSignInService;
import vn.tsolve.identity.signin.port.TenantScope;

/** AC-001.15: a sign-in on one Workspace reaches that Workspace's database only, whatever the other one holds. */
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
class CallbackAccountIsolationIT {

    @Autowired
    private TenantScope scope;

    @Autowired
    private AccountSignInService signInService;

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(AccountSignInService.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void clean() {
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        serviceLogger.detachAppender(logs);
    }

    @Test
    @DisplayName(
            "AC-001.15 / edge 24 a sign-in on Workspace A with the email of Workspace B's Account finds nothing and leaves B's Account untouched")
    void aSignInOnAFindsNothingOfBsAccount_AC_001_15_edge24() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        JdbcTemplate b = TwoTenantDatabases.tenantB();
        b.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('lan@beta.example', 'Beta Lan', 'ACTIVE', now())");

        AccountSignInService.Result result =
                scope.runIn(TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "lan@beta.example", "Lan"));

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.NO_ACCOUNT);
        Map<String, Object> row =
                b.queryForMap("SELECT keycloak_sub, full_name, first_sign_in_at, last_sign_in_at FROM app_user");
        assertThat(row.get("keycloak_sub")).isNull();
        assertThat(row.get("full_name")).isEqualTo("Beta Lan");
        assertThat(row.get("first_sign_in_at")).isNull();
        assertThat(row.get("last_sign_in_at")).isNull();
        assertThat(a.queryForObject("SELECT count(*) FROM app_user", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("AC-001.15 a sub bound in Workspace B neither matches nor blocks an Account of Workspace A")
    void aSubBoundInBNeitherMatchesNorBlocksA_AC_001_15() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        JdbcTemplate b = TwoTenantDatabases.tenantB();
        b.update("INSERT INTO app_user (email, full_name, status, created_at, keycloak_sub)"
                + " VALUES ('lan@beta.example', 'Beta Lan', 'ACTIVE', now(), 'kc-sub-1')");
        a.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('lan@acme.example', 'Acme Lan', 'ACTIVE', now())");
        UUID acmeId = a.queryForObject("SELECT id FROM app_user", UUID.class);

        AccountSignInService.Result result =
                scope.runIn(TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "lan@acme.example", "Lan"));

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(result.accountId()).isEqualTo(acmeId);
        assertThat(a.queryForObject("SELECT keycloak_sub FROM app_user", String.class))
                .isEqualTo("kc-sub-1");
        Map<String, Object> row =
                b.queryForMap("SELECT email, keycloak_sub, full_name, first_sign_in_at, last_sign_in_at FROM app_user");
        assertThat(row.get("email")).isEqualTo("lan@beta.example");
        assertThat(row.get("keycloak_sub")).isEqualTo("kc-sub-1");
        assertThat(row.get("full_name")).isEqualTo("Beta Lan");
        assertThat(row.get("first_sign_in_at")).isNull();
        assertThat(row.get("last_sign_in_at")).isNull();
        assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.WARN)).isEmpty();
    }
}
