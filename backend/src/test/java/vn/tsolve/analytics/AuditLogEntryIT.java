package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.audit.impl.AuditServiceImpl;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.testsupport.SharedPostgres;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuditServiceImpl.class, JacksonAutoConfiguration.class})
class AuditLogEntryIT {
    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    @Autowired
    private AuditService audit;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager manager;

    private final TSolveUser user = new TSolveUser(UUID.randomUUID(), "audit@example.test", "Audit User", Set.of());
    private UUID target;

    @BeforeEach
    void signIn() {
        target = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storesRowAndOptionalDetails_AC_002_1() {
        Instant before = Instant.now();
        new TransactionTemplate(manager)
                .executeWithoutResult(s -> audit.record(AuditAction.IMPORT, "PROJECT", target, Map.of("created", 2)));
        var row = jdbc.queryForMap("select * from audit_log_entry where target_id = ?", target);
        assertThat(row)
                .containsEntry("actor_id", user.id())
                .containsEntry("actor_email", user.email())
                .containsEntry("action", "IMPORT")
                .containsEntry("target_type", "PROJECT")
                .containsEntry("target_id", target);
        assertThat(row.get("audit_log_entry_id")).isInstanceOf(UUID.class);
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isBetween(before, Instant.now());
        assertThat(jdbc.queryForObject(
                        "select details->>'created' from audit_log_entry where target_id = ?", String.class, target))
                .isEqualTo("2");
        UUID other = UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(s -> audit.record(AuditAction.APPROVE, "TICKET", other));
        assertThat(jdbc.queryForObject(
                        "select details is null from audit_log_entry where target_id = ?", Boolean.class, other))
                .isTrue();
    }

    @Test
    void refusesWithoutTransaction_AC_002_2() {
        assertThatThrownBy(() -> audit.record(AuditAction.DELETE, "TICKET", target))
                .isInstanceOf(IllegalStateException.class);
        assertAbsent();
    }

    @Test
    void refusesWithoutUser_AC_002_3() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> new TransactionTemplate(manager)
                        .executeWithoutResult(s -> audit.record(AuditAction.DELETE, "TICKET", target)))
                .isInstanceOf(IllegalStateException.class);
        assertAbsent();
    }

    @Test
    void rollsBackWithCaller_AC_002_4() {
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            audit.record(AuditAction.DELETE, "TICKET", target);
            s.setRollbackOnly();
        });
        assertAbsent();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "update audit_log_entry set action = 'DELETE' where target_id = ?",
                "delete from audit_log_entry where target_id = ?",
                "truncate audit_log_entry"
            })
    void refusesMutation_AC_002_5(String sql) {
        new TransactionTemplate(manager).executeWithoutResult(s -> audit.record(AuditAction.APPROVE, "TICKET", target));
        assertThatThrownBy(() -> {
                    if (sql.contains("?")) {
                        jdbc.update(sql, target);
                    } else {
                        jdbc.execute(sql);
                    }
                })
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("select action from audit_log_entry where target_id = ?", String.class, target))
                .isEqualTo("APPROVE");
    }

    @Test
    void replacesLegacyTableWithRequiredColumns_AC_002_6() {
        assertThat(jdbc.queryForObject("select to_regclass('audit_event') is null", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.columns where table_schema='public' and table_name='audit_log_entry'",
                        String.class))
                .containsExactlyInAnyOrder(
                        "audit_log_entry_id",
                        "actor_id",
                        "actor_email",
                        "action",
                        "target_type",
                        "target_id",
                        "details",
                        "created_at");
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_indexes where tablename='audit_log_entry' and indexdef like '%(created_at)%'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void runsOnPostgres16_AC_002_11() {
        assertThat(jdbc.queryForObject("show server_version", String.class)).startsWith("16.");
    }

    private void assertAbsent() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_log_entry where target_id = ?", Integer.class, target))
                .isZero();
    }
}
