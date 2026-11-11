package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.tsolve.analytics.dto.AuditEntryResponse;
import vn.tsolve.analytics.mapper.AuditEntryMapperImpl;
import vn.tsolve.analytics.service.AuditLogService;
import vn.tsolve.analytics.service.impl.AuditLogServiceImpl;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.testsupport.SharedPostgres;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditLogServiceImpl.class, AuditEntryMapperImpl.class})
class AuditLogIT {
    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditLogService service;

    @Test
    void mapsContractFields_AC_003_1() {
        UUID id = insert("pm@example.test", "APPROVE", "2026-01-01T00:00:00Z");
        AuditEntryResponse row = service.findEntries(null, null, null, null, 0, 20, List.of())
                .getContent()
                .getFirst();
        assertThat(row.id()).isEqualTo(id);
        assertThat(row.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(row.actor()).isEqualTo("pm@example.test");
        assertThat(row.action()).isEqualTo(AuditAction.APPROVE);
        assertThat(row.resourceType()).isEqualTo("TICKET");
        assertThat(row.resourceId()).isEqualTo(id);
        assertThat(row.summary()).isNull();
    }

    @Test
    void matchesActorLiterallyAndIgnoresBlank_AC_003_3() {
        UUID literal = insert("PM%_\\name@example.test", "APPROVE", "2026-01-01T00:00:00Z");
        insert("pmXXname@example.test", "APPROVE", "2026-01-01T00:00:00Z");
        assertThat(service.findEntries("  pm%_\\NAME  ", null, null, null, 0, 20, List.of()))
                .extracting(AuditEntryResponse::id)
                .containsExactly(literal);
        assertThat(service.findEntries("%", null, null, null, 0, 20, List.of())).hasSize(1);
        assertThat(service.findEntries("_", null, null, null, 0, 20, List.of())).hasSize(1);
        assertThat(service.findEntries("   ", null, null, null, 0, 20, List.of()))
                .hasSize(2);
    }

    @Test
    void filtersExactAction_AC_003_4() {
        UUID match = insert("pm@example.test", "REJECT", "2026-01-01T00:00:00Z");
        insert("pm@example.test", "APPROVE", "2026-01-01T00:00:00Z");
        assertThat(service.findEntries(null, "REJECT", null, null, 0, 20, List.of()))
                .extracting(AuditEntryResponse::id)
                .containsExactly(match);
    }

    @Test
    void includesWholeVietnamDayAndCombinesFilters_AC_003_5() {
        UUID start = insert("pm@example.test", "IMPORT", "2026-01-01T17:00:00Z");
        UUID end = insert("pm@example.test", "IMPORT", "2026-01-02T16:59:59.999Z");
        insert("pm@example.test", "IMPORT", "2026-01-02T17:00:00Z");
        insert("pm@example.test", "IMPORT", "2026-01-01T16:59:59.999Z");
        insert("other@example.test", "IMPORT", "2026-01-02T00:00:00Z");
        insert("pm@example.test", "APPROVE", "2026-01-02T00:00:00Z");
        assertThat(service.findEntries("PM@", "IMPORT", "2026-01-02", "2026-01-02", 0, 20, List.of()))
                .extracting(AuditEntryResponse::id)
                .containsExactly(end, start);
    }

    @Test
    void ordersEqualTimestampsAcrossPages_AC_003_2() {
        UUID high = new UUID(0, 2);
        UUID low = new UUID(0, 1);
        insert(high, "pm@example.test", "APPROVE", "2026-01-01T00:00:00Z");
        insert(low, "pm@example.test", "APPROVE", "2026-01-01T00:00:00Z");
        assertThat(service.findEntries(null, null, null, null, 0, 1, List.of()))
                .extracting(AuditEntryResponse::id)
                .containsExactly(low);
        assertThat(service.findEntries(null, null, null, null, 1, 1, List.of()))
                .extracting(AuditEntryResponse::id)
                .containsExactly(high);
    }

    private UUID insert(String actor, String action, String time) {
        UUID id = UUID.randomUUID();
        insert(id, actor, action, time);
        return id;
    }

    private void insert(UUID id, String actor, String action, String time) {
        jdbc.update(
                "insert into audit_log_entry (audit_log_entry_id, actor_email, action, target_type, target_id, created_at) values (?, ?, ?, 'TICKET', ?, ?)",
                id,
                actor,
                action,
                id,
                Timestamp.from(Instant.parse(time)));
    }
}
