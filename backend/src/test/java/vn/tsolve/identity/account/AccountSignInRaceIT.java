package vn.tsolve.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Two sign-ins, or a sign-in and a deactivation, at the same moment. A test-only wrapper around the repository runs the
 * competing write through JDBC right after one named repository call returns, so the interleaving is deterministic.
 */
@SpringBootTest
@Import({TwoTenantDatabases.class, AccountSignInRaceIT.Hooks.class})
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
class AccountSignInRaceIT {

    /** Repository method name to the competing write that runs right after its first call. */
    private static final Map<String, Runnable> AFTER = new ConcurrentHashMap<>();

    /** A competing write armed by the bind, to run just before the next repository call. */
    private static final java.util.concurrent.atomic.AtomicReference<Runnable> NEXT_CALL =
            new java.util.concurrent.atomic.AtomicReference<>();

    @TestConfiguration
    static class Hooks {
        @Bean
        static BeanPostProcessor accountRepositoryHooks() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof AccountRepository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            bean.getClass().getClassLoader(),
                            bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                Runnable due = NEXT_CALL.getAndSet(null);
                                if (due != null) {
                                    due.run();
                                }
                                Object result;
                                try {
                                    result = method.invoke(bean, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                                Runnable hook = AFTER.remove(method.getName());
                                if (hook != null) {
                                    if (method.getName().equals("bindKeycloakSub")) {
                                        // the bind runs inside a transaction that still holds the row lock: wait for
                                        // the next repository call, which is after the commit
                                        NEXT_CALL.set(hook);
                                    } else {
                                        hook.run();
                                    }
                                }
                                return result;
                            });
                }
            };
        }
    }

    @Autowired
    private TenantScope scope;

    @Autowired
    private AccountSignInService signInService;

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(AccountSignInService.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void clean() {
        AFTER.clear();
        NEXT_CALL.set(null);
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
        TwoTenantDatabases.tenantB().update("TRUNCATE app_user CASCADE");
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        AFTER.clear();
        serviceLogger.detachAppender(logs);
    }

    private static UUID insertSubLess() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        a.update("INSERT INTO app_user (email, full_name, status, created_at)"
                + " VALUES ('lan@acme.example', 'Lan', 'ACTIVE', now())");
        return a.queryForObject("SELECT id FROM app_user", UUID.class);
    }

    private long warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }

    private AccountSignInService.Result signIn() {
        return scope.runIn(TwoTenantDatabases.ACME, () -> signInService.signIn("kc-sub-1", "lan@acme.example", "Lan"));
    }

    @Test
    @DisplayName(
            "AC-001.56 two tabs: when another sign-in binds the SAME sub between lookup and bind, this one signs in too (no MSG09, no WARN)")
    void aSecondFirstSignInOfTheSameSubSignsIn_AC_001_56() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        UUID id = insertSubLess();
        AFTER.put("findByEmail", () -> a.update("UPDATE app_user SET keycloak_sub = 'kc-sub-1'"));

        AccountSignInService.Result result = signIn();

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SIGNED_IN);
        assertThat(result.accountId()).isEqualTo(id);
        assertThat(a.queryForObject("SELECT keycloak_sub FROM app_user", String.class))
                .isEqualTo("kc-sub-1");
        assertThat(a.queryForObject("SELECT last_sign_in_at FROM app_user", Object.class))
                .isNotNull();
        assertThat(warnings()).isZero();
    }

    @Test
    @DisplayName(
            "AC-001.56 when another sign-in binds a DIFFERENT sub between lookup and bind, this one is a SUB_CONFLICT")
    void aSecondFirstSignInOfAnotherSubIsAConflict_AC_001_56() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        UUID id = insertSubLess();
        AFTER.put("findByEmail", () -> a.update("UPDATE app_user SET keycloak_sub = 'kc-sub-other'"));

        AccountSignInService.Result result = signIn();

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.SUB_CONFLICT);
        assertThat(result.accountId()).isEqualTo(id);
        assertThat(a.queryForObject("SELECT keycloak_sub FROM app_user", String.class))
                .isEqualTo("kc-sub-other");
        assertThat(warnings()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "AC-001.13 / I2 an Account deactivated between lookup and bind is reported DEACTIVATED, gets no sub, and stays Deactivated")
    void aDeactivationBeforeTheBindIsReportedDeactivated_AC_001_13() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        UUID id = insertSubLess();
        AFTER.put("findByEmail", () -> a.update("UPDATE app_user SET status = 'DEACTIVATED'"));

        AccountSignInService.Result result = signIn();

        assertThat(result.status()).isEqualTo(AccountSignInService.Status.DEACTIVATED);
        assertThat(result.accountId()).isEqualTo(id);
        assertThat(a.queryForObject("SELECT keycloak_sub FROM app_user", String.class))
                .isNull();
        assertThat(a.queryForObject("SELECT status FROM app_user", String.class))
                .isEqualTo("DEACTIVATED");
        assertThat(a.queryForObject("SELECT last_sign_in_at FROM app_user", Object.class))
                .isNull();
        assertThat(warnings()).isZero();
    }

    @Test
    @DisplayName("AC-001.13 / I2 an Account deactivated between the bind and the final write is not set back to ACTIVE")
    void aDeactivationAfterTheBindIsNotUndone_AC_001_13() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        insertSubLess();
        AFTER.put("bindKeycloakSub", () -> a.update("UPDATE app_user SET status = 'DEACTIVATED'"));

        signIn();

        assertThat(a.queryForObject("SELECT status FROM app_user", String.class))
                .isEqualTo("DEACTIVATED");
    }

    @Test
    @DisplayName(
            "AC-001.13 / I2 an Account deactivated between the sub lookup and the final write is not set back to ACTIVE")
    void aDeactivationAfterTheSubLookupIsNotUndone_AC_001_13() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        a.update("INSERT INTO app_user (email, full_name, status, created_at, keycloak_sub)"
                + " VALUES ('lan@acme.example', 'Lan', 'ACTIVE', now(), 'kc-sub-1')");
        AFTER.put("findByKeycloakSub", () -> a.update("UPDATE app_user SET status = 'DEACTIVATED'"));

        signIn();

        assertThat(a.queryForObject("SELECT status FROM app_user", String.class))
                .isEqualTo("DEACTIVATED");
    }
}
