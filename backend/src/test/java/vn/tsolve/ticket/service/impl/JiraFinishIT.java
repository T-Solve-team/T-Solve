package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.testsupport.SharedPostgres;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ImportConfirmService;

/** Finish of a Jira preview against a real PostgreSQL: the concurrency cases mocks cannot show (Edge 6, 7, 11). */
@SpringBootTest
@TestPropertySource(
        properties = {
            "tsolve.stub.projects=pm-a:00000000-0000-0000-0000-000000000001,00000000-0000-0000-0000-000000000002",
            "tsolve.stub.inactive-projects=00000000-0000-0000-0000-000000000002",
            "tsolve.cors.allowed-origins=http://localhost:5173",
            "server.port=0",
            // the sign-in values the application needs to start (identity)
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class JiraFinishIT {

    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    // Finish records an audit event, which needs a signed-in user; this test is about the import itself
    @org.springframework.boot.test.mock.mockito.MockBean
    private vn.tsolve.common.audit.AuditService audit;

    private static final String PM = "pm-a";
    private static final UUID ACTIVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INACTIVE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private ImportConfirmService confirms;

    @Autowired
    private ImportPreviewRepository previews;

    @Autowired
    private ImportPreviewRowRepository previewRows;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("delete from ticket_import_decision");
        jdbc.execute("delete from ticket");
        jdbc.execute("delete from import_batch");
        jdbc.execute("delete from import_preview");
    }

    /** A pulled Jira preview whose issues all have a Solution. */
    private long jiraPreview(UUID project, String... keys) {
        ImportPreview preview = previews.save(ImportPreview.jira(
                PM, project, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false, Instant.now()));
        int rowNo = 1;
        for (String key : keys) {
            previewRows.save(new ImportPreviewRow(
                    preview.getId(),
                    rowNo++,
                    new ImportPreviewRow.Values(
                            key,
                            "Login fails",
                            "Login page answers 500",
                            "Anh",
                            "2026-10-02",
                            "Error 500",
                            "Bad token",
                            "Restart",
                            null,
                            "All users"),
                    List.of(),
                    ImportType.WITH_SOLUTION));
        }
        return preview.getId();
    }

    private static String insertTicket(String key) {
        return "insert into ticket (source_ticket_id, project_id, source, status, title, symptoms, root_cause,"
                + " resolution_steps, scope) values ('" + key + "', '" + ACTIVE
                + "', 'MANUAL', 'PUBLISHED', 't', 's', 'r', 'st', 'sc')";
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void aTicketCreatedDuringFinishIsSkippedAfterOneRetry_AC_003_2() throws Exception {
        // Edge 6: KAN-2 becomes a Ticket after Finish read the taken keys, before it inserts KAN-2
        long preview = jiraPreview(ACTIVE, "KAN-1", "KAN-2", "KAN-3");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<ImportResultResponse> finish;
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            other.createStatement().executeUpdate(insertTicket("KAN-2"));
            finish = pool.submit(() -> confirms.confirm(PM, preview));
            // Finish waits on the unique index until the other transaction ends
            waitForALockWait();
            other.commit();
        }

        ImportResultResponse result = finish.get();
        pool.shutdown();

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.notice()).isEqualTo(new MessageRef("MSG38", Map.of("count", 1, "issue_keys", "KAN-2")));
        assertThat(jdbc.queryForList(
                        "select source_ticket_id from ticket where source = 'JIRA_IMPORT' order by source_ticket_id",
                        String.class))
                .containsExactly("KAN-1", "KAN-3");
        assertThat(jdbc.queryForList("select status from import_batch", String.class))
                .containsExactly("COMPLETED");
        assertThat(count("import_preview")).isZero();
    }

    private void waitForALockWait() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Integer waiting = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock'"
                            + " and datname = current_database()",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Finish never waited for the other transaction");
    }

    @Test
    void aDoubleClickOnFinishImportsOnce_AC_003_8() throws Exception {
        // Edge 7
        long preview = jiraPreview(ACTIVE, "KAN-1", "KAN-2");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> finish = () -> {
            start.await();
            try {
                confirms.confirm(PM, preview);
                return "ok";
            } catch (NotFoundException e) {
                return "404";
            }
        };
        Future<String> a = pool.submit(finish);
        Future<String> b = pool.submit(finish);
        start.countDown();

        List<String> outcomes = List.of(a.get(), b.get());
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("ok", "404");
        assertThat(count("ticket")).isEqualTo(2);
        assertThat(jdbc.queryForList("select status from import_batch", String.class))
                .containsExactly("COMPLETED");
    }

    @Test
    void aDraftOfAProjectNoLongerActiveIsKept_AC_003_4() {
        // Edge 11
        long preview = jiraPreview(INACTIVE, "KAN-1");
        ImportPreview draft = previews.findById(preview).orElseThrow();
        draft.saveAsDraft(Instant.now());
        previews.save(draft);

        MessageCodeException e = catchThrowableOfType(() -> confirms.confirm(PM, preview), MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG40");
        assertThat(count("ticket")).isZero();
        assertThat(count("import_batch")).isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from import_preview where draft_saved_at is not null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void finishingADraftDeletesItWithItsRows_AC_003_10() {
        long preview = jiraPreview(ACTIVE, "KAN-1");
        ImportPreview draft = previews.findById(preview).orElseThrow();
        draft.saveAsDraft(Instant.now());
        previews.save(draft);

        confirms.confirm(PM, preview);

        assertThat(count("import_preview")).isZero();
        assertThat(count("import_preview_row")).isZero();
        assertThat(count("ticket")).isEqualTo(1);
    }
}
