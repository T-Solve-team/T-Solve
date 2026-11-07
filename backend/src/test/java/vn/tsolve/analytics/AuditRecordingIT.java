package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.common.audit.impl.AuditServiceImpl;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.config.TicketClockConfig;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.ImportFailedException;
import vn.tsolve.ticket.exception.ProjectNotActiveException;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.ProjectAccess;
import vn.tsolve.ticket.service.TicketDecisionService;
import vn.tsolve.ticket.service.TicketDecisionService.Action;
import vn.tsolve.ticket.service.impl.ImportBatchRecorder;
import vn.tsolve.ticket.service.impl.ImportConfirmServiceImpl;
import vn.tsolve.ticket.service.impl.TicketDecisionServiceImpl;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    TicketClockConfig.class,
    JacksonAutoConfiguration.class,
    AuditServiceImpl.class,
    TicketDecisionServiceImpl.class,
    ImportConfirmServiceImpl.class,
    ImportBatchRecorder.class
})
class AuditRecordingIT {
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TicketDecisionService decisions;

    @Autowired
    private ImportConfirmService imports;

    @Autowired
    private ImportPreviewRepository previews;

    @Autowired
    private ImportPreviewRowRepository rows;

    @Autowired
    private PlatformTransactionManager manager;

    @MockBean
    private ProjectAccess projects;

    private UUID project;
    private TSolveUser user;

    @BeforeEach
    void seedActor() {
        project = UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        UUID id = jdbc.queryForObject(
                "insert into app_user (email, status, created_at) values (?, 'ACTIVE', now()) returning id",
                UUID.class,
                email);
        user = new TSolveUser(id, email, "Reviewer", Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        when(projects.isActive(any())).thenReturn(true);
        when(projects.managesActive(anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void recordsEachTicketInMaximumBatch_AC_002_7(Action action) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            ids.add(ticket("PENDING_REVIEW"));
        }
        decisions.decide(user.id(), Set.of(project), ids, action, action == Action.APPROVED ? null : "Please revise");
        String expected =
                switch (action) {
                    case APPROVED -> "APPROVE";
                    case REJECTED -> "REJECT";
                    case CHANGES_REQUESTED -> "REQUEST_CHANGES";
                };
        assertThat(jdbc.queryForList(
                        "select target_id from audit_log_entry where actor_id = ? and action = ? and target_type = 'TICKET'",
                        UUID.class,
                        user.id(),
                        expected))
                .containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void refusedDecisionWritesNothing_AC_002_8() {
        UUID pending = ticket("PENDING_REVIEW");
        UUID stale = ticket("REJECTED");
        assertThatThrownBy(() ->
                        decisions.decide(user.id(), Set.of(project), List.of(pending, stale), Action.APPROVED, null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() ->
                        decisions.decide(user.id(), Set.of(UUID.randomUUID()), List.of(pending), Action.APPROVED, null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> decisions.decide(user.id(), Set.of(project), List.of(pending), Action.REJECTED, " "))
                .isInstanceOf(RuntimeException.class);
        assertNoEntries();
        assertThat(jdbc.queryForObject("select status from ticket where ticket_id = ?", String.class, pending))
                .isEqualTo("PENDING_REVIEW");
    }

    @Test
    void successfulImportRecordsBatchAndCounts_AC_002_9() {
        long preview = preview("NEW-" + UUID.randomUUID());
        jdbc.update(
                "insert into import_preview_row (preview_id, row_no, status) values (?, 2, 'ERROR'), (?, 3, 'REMOVED')",
                preview,
                preview);
        var result = imports.confirm(user.email(), preview);
        var entry = jdbc.queryForMap(
                "select action, target_type, target_id, details->>'importBatchId' as batch, details->>'created' as created, details->>'skipped' as skipped from audit_log_entry where actor_id = ?",
                user.id());
        assertThat(entry)
                .containsEntry("action", "IMPORT")
                .containsEntry("target_type", "PROJECT")
                .containsEntry("target_id", project)
                .containsEntry("batch", Long.toString(result.importBatchId()))
                .containsEntry("created", "1")
                .containsEntry("skipped", "2");
    }

    @Test
    void refusedAndFailedImportWriteNothing_AC_002_10() {
        UUID existing = ticket("PUBLISHED");
        String key =
                jdbc.queryForObject("select source_ticket_id from ticket where ticket_id = ?", String.class, existing);
        long preview = preview(key);
        when(projects.isActive(project)).thenReturn(false);
        assertThatThrownBy(() -> imports.confirm(user.email(), preview)).isInstanceOf(ProjectNotActiveException.class);
        when(projects.isActive(project)).thenReturn(true);
        assertThatThrownBy(() -> imports.confirm(user.email(), preview)).isInstanceOf(ImportFailedException.class);
        assertNoEntries();
        assertThat(jdbc.queryForObject("select status from import_batch where project_id = ?", String.class, project))
                .isEqualTo("FAILED");
    }

    @Test
    void databaseFailureAfterSavingRowsRollsBackImport_AC_002_10() {
        long preview = preview("NEW-" + UUID.randomUUID());
        // The writer runs after ticket/decision saves; force its INSERT to fail in the same transaction.
        jdbc.execute(
                "create function fail_test_audit_import() returns trigger language plpgsql as $$ begin if NEW.actor_id = '"
                        + user.id() + "'::uuid then raise exception 'test failure'; end if; return NEW; end; $$");
        jdbc.execute(
                "create trigger fail_test_audit_import before insert on audit_log_entry for each row execute function fail_test_audit_import()");
        try {
            assertThatThrownBy(() -> imports.confirm(user.email(), preview)).isInstanceOf(ImportFailedException.class);
            assertNoEntries();
            assertThat(jdbc.queryForObject("select count(*) from ticket where project_id = ?", Integer.class, project))
                    .isZero();
            assertThat(jdbc.queryForObject(
                            "select status from import_batch where project_id = ?", String.class, project))
                    .isEqualTo("FAILED");
        } finally {
            jdbc.execute("drop trigger fail_test_audit_import on audit_log_entry");
            jdbc.execute("drop function fail_test_audit_import()");
        }
    }

    private UUID ticket(String status) {
        return jdbc.queryForObject(
                "insert into ticket (source_ticket_id, project_id, source, status, title, symptoms, root_cause, resolution_steps, scope, review_due_date) values (?, ?, 'SOLUTION_FORM', ?, 'Title', 'Symptom', 'Cause', 'Fix', 'Users', '2026-11-12') returning ticket_id",
                UUID.class,
                UUID.randomUUID().toString(),
                project,
                status);
    }

    private long preview(String key) {
        return new TransactionTemplate(manager).execute(s -> {
            var preview = previews.saveAndFlush(new ImportPreview(
                    user.email(), project, TicketSource.CSV, ImportType.WITH_SOLUTION, "tickets.csv", Instant.now()));
            rows.saveAndFlush(new ImportPreviewRow(
                    preview.getId(),
                    1,
                    new ImportPreviewRow.Values(
                            key, "Title", null, null, null, "Symptom", "Cause", "Fix", null, "Users"),
                    List.of(),
                    ImportType.WITH_SOLUTION));
            return preview.getId();
        });
    }

    private void assertNoEntries() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_log_entry where actor_id = ?", Integer.class, user.id()))
                .isZero();
    }
}
