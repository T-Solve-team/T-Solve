package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.TicketTestDatabase;
import vn.tsolve.ticket.dto.ManualTicketRequest;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.ImportFailedException;
import vn.tsolve.ticket.exception.JiraUnavailableException;
import vn.tsolve.ticket.mapper.ImportPreviewMapper;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.JiraIssueSource;
import vn.tsolve.ticket.service.ManualTicketService;
import vn.tsolve.ticket.service.ProjectAccess;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(TicketTestDatabase.class)
@TestPropertySource(
        properties = {
            "tsolve.stub.projects=11111111-1111-1111-1111-111111111111:00000000-0000-0000-0000-000000000001",
            "tsolve.cors.allowed-origins=http://localhost:5173",
            "server.port=0",
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
class ImportHistoryDateRangeIT {
    private static final UUID PROJECT = new UUID(0, 1);
    private static final TSolveUser PM = new TSolveUser(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "range-pm@example.test",
            "Nguyễn Văn An",
            Set.of(Role.PROJECT_MANAGER));
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    @MockBean
    private AuditService audit;

    @Autowired
    private ImportConfirmService confirms;

    @Autowired
    private ManualTicketService manual;

    @Autowired
    private ImportBatchRecorder recorder;

    @Autowired
    private ImportPreviewRepository previews;

    @Autowired
    private ImportPreviewRowRepository rows;

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private ProjectAccess projects;

    @Autowired
    private ImportPreviewMapper mapper;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("delete from ticket_import_decision");
        jdbc.execute("delete from ticket");
        jdbc.execute("delete from import_batch");
        jdbc.execute("delete from import_preview");
        jdbc.update(
                "insert into app_user (id, email, full_name, status, created_at) values (?, ?, ?, 'ACTIVE', now())"
                        + " on conflict do nothing",
                PM.id(),
                PM.email(),
                PM.fullName());
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbc.execute("drop trigger if exists import_history_fail on ticket_import_decision");
        jdbc.execute("drop function if exists import_history_fail()");
    }

    private long preview(TicketSource source) {
        ImportPreview preview = previews.save(
                source == TicketSource.JIRA_IMPORT
                        ? ImportPreview.jira(PM.getName(), PROJECT, FROM, TO, false, Instant.now())
                        : new ImportPreview(
                                PM.getName(), PROJECT, source, ImportType.WITH_SOLUTION, "tickets.csv", Instant.now()));
        rows.save(new ImportPreviewRow(
                preview.getId(),
                1,
                new ImportPreviewRow.Values(
                        "HISTORY-1",
                        "Login fails",
                        "Error 500",
                        "Anh",
                        "2026-10-02",
                        "Error 500",
                        "Bad token",
                        "Restart",
                        null,
                        "All users"),
                List.of(),
                ImportType.WITH_SOLUTION));
        return preview.getId();
    }

    private void failFinish() {
        jdbc.execute("create function import_history_fail() returns trigger language plpgsql as "
                + "$$ begin raise exception 'forced failure'; end $$");
        jdbc.execute("create trigger import_history_fail before insert on ticket_import_decision "
                + "for each row execute function import_history_fail()");
    }

    private void assertBatch(String source, String status, String from, String to) {
        Map<String, Object> batch = jdbc.queryForMap(
                "select source, status, date_from::text, date_to::text, published_count from import_batch");
        assertThat(batch)
                .containsEntry("source", source)
                .containsEntry("status", status)
                .containsEntry("date_from", from)
                .containsEntry("date_to", to)
                .containsEntry("published_count", "FAILED".equals(status) ? 0 : 1);
    }

    @Test
    @DisplayName("AC-007.8 successful Jira Finish persists the resolved range")
    void successfulFinish_AC_007_8() {
        confirms.confirm(PM.getName(), preview(TicketSource.JIRA_IMPORT));
        assertBatch("JIRA_IMPORT", "COMPLETED", FROM.toString(), TO.toString());
    }

    @Test
    @DisplayName("AC-007.8 failed Jira Finish persists the range after rollback")
    void failedFinish_AC_007_8() {
        long previewId = preview(TicketSource.JIRA_IMPORT);
        failFinish();
        assertThatThrownBy(() -> confirms.confirm(PM.getName(), previewId)).isInstanceOf(ImportFailedException.class);
        assertBatch("JIRA_IMPORT", "FAILED", FROM.toString(), TO.toString());
        assertThat(jdbc.queryForObject("select count(*) from ticket", Integer.class))
                .isZero();
        assertThat(previews.findById(previewId)).isPresent();
    }

    @Test
    @DisplayName("AC-007.8 failed Jira pull persists the requested resolved range")
    void failedPull_AC_007_8() {
        JiraIssueSource jira = mock(JiraIssueSource.class);
        when(jira.resolvedIssues(anyString(), any(), any(), any(), any()))
                .thenThrow(new JiraUnavailableException("HTTP 503"));
        JiraImportServiceImpl service = new JiraImportServiceImpl(
                jira, previews, rows, tickets, projects, recorder, mapper, clock, transactions);
        assertThatThrownBy(() -> service.pull(PM.getName(), PROJECT, FROM, TO))
                .isInstanceOf(JiraUnavailableException.class);
        assertBatch("JIRA_IMPORT", "FAILED", FROM.toString(), TO.toString());
    }

    @Test
    @DisplayName("AC-007.8 CSV Finish keeps no Jira range")
    void csvFinish_AC_007_8() {
        confirms.confirm(PM.getName(), preview(TicketSource.CSV));
        assertBatch("CSV", "COMPLETED", null, null);
    }

    @Test
    @DisplayName("AC-007.8 failed CSV Finish keeps no Jira range")
    void failedCsvFinish_AC_007_8() {
        long previewId = preview(TicketSource.CSV);
        failFinish();
        assertThatThrownBy(() -> confirms.confirm(PM.getName(), previewId)).isInstanceOf(ImportFailedException.class);
        assertBatch("CSV", "FAILED", null, null);
    }

    @Test
    @DisplayName("AC-007.8 Manual batches keep no Jira range")
    void manualEntry_AC_007_8() {
        manual.create(
                PM,
                new ManualTicketRequest(
                        PROJECT,
                        "HISTORY-1",
                        "Login fails",
                        "Error 500",
                        "2026-10-02",
                        "Error 500",
                        "Bad token",
                        "Restart",
                        null,
                        "All users",
                        null));
        assertBatch("MANUAL", "COMPLETED", null, null);
    }
}
