package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ManualEntryResponse;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.service.ImportDraftService;
import vn.tsolve.ticket.service.ManualEntryService;

/** Manual entry against a real PostgreSQL: the rows, the after-commit event, the unique Ticket ID and the rollback. */
@SpringBootTest
@TestPropertySource(
        properties = {
            "tsolve.stub.projects=00000000-0000-0000-0000-0000000000aa:00000000-0000-0000-0000-000000000001;00000000-0000-0000-0000-0000000000bb:00000000-0000-0000-0000-000000000001",
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
class ManualEntryIT {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final TSolveUser PM = new TSolveUser(
            UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
            "pm-a@example.test",
            "Nguyen Van An",
            Set.of(Role.PROJECT_MANAGER));
    private static final TSolveUser BOOM = new TSolveUser(
            UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
            "boom@example.test",
            "Boom",
            Set.of(Role.PROJECT_MANAGER));

    // the container of ImportConfirmIT, so this class starts none of its own (backend/AGENTS.md §8)
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = ImportConfirmIT.DB;

    @MockBean
    private AuditService audit;

    @Autowired
    private ManualEntryService manualEntries;

    @Autowired
    private MultipartProperties multipart;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ImportConfirmIT.PublishedEvents published;

    @Autowired
    private ManualEntryFileStorage storage;

    @Autowired
    private ImportDraftService drafts;

    @Autowired
    private PreviewCleanupJob cleanup;

    @TestConfiguration
    static class Listener {
        @Bean
        ImportConfirmIT.PublishedEvents publishedEvents(DataSource dataSource) {
            return new ImportConfirmIT.PublishedEvents(dataSource);
        }

        // no object storage runs here; the platform module tests the real one (ObjectFileStorageIT)
        @Bean
        ManualEntryFileStorage fileStorage() {
            return new ManualEntryFileStorage();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("delete from import_preview");
        jdbc.execute("delete from ticket_import_decision");
        jdbc.execute("delete from ticket");
        jdbc.execute("delete from import_batch");
        jdbc.execute("drop trigger if exists fail_for_boom on ticket_import_decision");
        for (TSolveUser user : new TSolveUser[] {PM, BOOM}) {
            jdbc.update(
                    "insert into app_user (id, email, status, created_at) values (?, ?, 'ACTIVE', now())"
                            + " on conflict do nothing",
                    user.id(),
                    user.email());
        }
        published.seen.clear();
        published.failNext = false;
        storage.reset();
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    private static String entry(String ticketId) {
        return "{\"projectId\":\"" + PROJECT + "\",\"ticketId\":\"" + ticketId + "\",\"title\":\"Login fails\","
                + "\"description\":\"Users cannot sign in\",\"resolvedDate\":\"2026-11-01\","
                + "\"symptoms\":\"Error 500\",\"rootCause\":\"Bad token\",\"resolutionSteps\":\"Restart\","
                + "\"scope\":\"All users\"}";
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void savesTheTicketItsDecisionAndACompletedBatchInOneTransaction_AC_006_1() {
        ManualEntryResponse response = manualEntries.save(PM, entry(" T-1 "), List.of());

        assertThat(jdbc.queryForList("select ticket_id, source_ticket_id, source, status, writer, writer_id,"
                        + " resolved_date, symptoms, workaround, import_batch_id from ticket"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("ticket_id")).isEqualTo(response.ticketId());
                    assertThat(row.get("source_ticket_id")).isEqualTo("T-1");
                    assertThat(row.get("source")).isEqualTo("MANUAL");
                    assertThat(row.get("status")).isEqualTo("PUBLISHED");
                    assertThat(row.get("writer")).isEqualTo("Nguyen Van An");
                    assertThat(row.get("writer_id")).isEqualTo(PM.id());
                    assertThat(row.get("resolved_date")).hasToString("2026-11-01");
                    assertThat(row.get("symptoms")).isEqualTo("Error 500");
                    assertThat(row.get("workaround")).isNull();
                    assertThat(row.get("import_batch_id")).isNotNull();
                });
        assertThat(jdbc.queryForList("select ticket_id, reviewer, decision from ticket_import_decision"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("ticket_id")).isEqualTo(response.ticketId());
                    assertThat(row.get("reviewer")).isEqualTo(PM.id().toString());
                    assertThat(row.get("decision")).isEqualTo("APPROVE");
                });
        assertThat(jdbc.queryForList("select source, status, published_count, file_name from import_batch"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("source")).isEqualTo("MANUAL");
                    assertThat(row.get("status")).isEqualTo("COMPLETED");
                    assertThat(row.get("published_count")).isEqualTo(1);
                    assertThat(row.get("file_name")).isNull();
                });
    }

    @Test
    void theTicketsPublishedEventArrivesAfterTheTicketIsCommitted_AC_006_10() {
        manualEntries.save(PM, entry("T-1"), List.of());

        assertThat(published.seen).containsExactly("ids=1 visible=1");
    }

    @Test
    void aTicketIdSavedByAnotherRequestWhileThisOneRunsIsMsg31_AC_006_8() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection other = jdbc.getDataSource().getConnection()) {
            // another request has inserted T-1 and not committed yet: the pre-check of this save cannot see it
            other.setAutoCommit(false);
            other.createStatement()
                    .execute("insert into ticket (source_ticket_id, project_id, source, status, title, symptoms,"
                            + " root_cause, resolution_steps, scope) values ('T-1', '" + PROJECT
                            + "', 'CSV', 'PUBLISHED', 't', 's', 'r', 'st', 'sc')");

            Future<String> save = pool.submit(() -> {
                try {
                    manualEntries.save(PM, entry("T-1"), List.of(file("a.png", new byte[] {1})));
                    return "ok";
                } catch (MessageCodeException e) {
                    return e.getStatus().value() + " " + e.getCode() + " "
                            + e.getParams().get("ticket_id");
                }
            });
            awaitWaitingLock();
            other.commit();

            assertThat(save.get()).isEqualTo("409 MSG31 T-1");
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForList("select source from ticket", String.class)).containsExactly("CSV");
        assertThat(count("evidence")).isZero();
        assertThat(storage.objects).isEmpty();
        assertThat(count("ticket_import_decision")).isZero();
        assertThat(count("import_batch")).isZero();
        assertThat(published.seen).isEmpty();
    }

    @Test
    void aFailureWhileSavingRollsBackTheTicketAndKeepsAFailedBatch_AC_006_11() {
        // the review decision, saved after the Ticket, fails inside the database
        jdbc.execute("create or replace function fail_for_boom() returns trigger language plpgsql as "
                + "$$ begin raise exception 'forced failure'; end $$");
        jdbc.execute("create trigger fail_for_boom before insert on ticket_import_decision "
                + "for each row when (new.reviewer = '" + BOOM.id() + "') execute function fail_for_boom()");

        MessageCodeException e = catchThrowableOfType(
                () -> manualEntries.save(BOOM, entry("T-1"), List.of(file("a.png", new byte[] {1}))),
                MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(e.getStatus().value()).isEqualTo(500);
        assertThat(count("ticket")).isZero();
        assertThat(count("ticket_import_decision")).isZero();
        assertThat(count("evidence")).isZero();
        assertThat(storage.objects).isEmpty();
        assertThat(jdbc.queryForList("select source, status, published_count, error from import_batch"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("source")).isEqualTo("MANUAL");
                    assertThat(row.get("status")).isEqualTo("FAILED");
                    assertThat(row.get("published_count")).isEqualTo(0);
                    assertThat((String) row.get("error")).contains("MSG33").doesNotContain("T-1", "Login fails");
                });
        assertThat(published.seen).isEmpty();
    }

    @Test
    void savesEachFileAsEvidenceOfTheTicketWithItsStorageKey_AC_006_2() {
        ManualEntryResponse response = manualEntries.save(
                PM,
                entry("T-1"),
                List.of(file("log.TXT", "line 1".getBytes(StandardCharsets.UTF_8)), file("shot.jpeg", new byte[] {1, 2
                })));

        assertThat(
                        jdbc.queryForList(
                                "select ticket_id, storage_key, file_name, file_type, file_size from evidence order by file_name"))
                .satisfiesExactly(
                        log -> {
                            assertThat(log.get("ticket_id")).isEqualTo(response.ticketId());
                            assertThat(log.get("file_name")).isEqualTo("log.TXT");
                            assertThat(log.get("file_type")).isEqualTo("txt");
                            assertThat(log.get("file_size")).isEqualTo(6L);
                            assertThat(storage.objects.get((String) log.get("storage_key")))
                                    .isEqualTo("line 1".getBytes(StandardCharsets.UTF_8));
                        },
                        shot -> {
                            assertThat(shot.get("file_type")).isEqualTo("jpg");
                            assertThat(shot.get("file_size")).isEqualTo(2L);
                            assertThat(storage.objects).containsKey((String) shot.get("storage_key"));
                        });
    }

    @Test
    void fiveFilesOf10MbEachAreAccepted_AC_006_7() {
        // edge 26: the most one manual entry may carry
        byte[] tenMb = new byte[10 * 1024 * 1024];
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            files.add(file(i + ".pdf", tenMb));
        }

        manualEntries.save(PM, entry("T-1"), files);

        assertThat(jdbc.queryForList("select file_size from evidence", Long.class))
                .hasSize(5)
                .containsOnly(10L * 1024 * 1024);
    }

    @Test
    void anyFileTheRequestLimitLetsThroughReachesTheMsg34Check_AC_006_6_BUG_214() {
        // a file over 10 MB is MSG34 with its name from EvidenceFiles, not 413 MSG28 from the multipart limit
        assertThat(multipart.getMaxFileSize()).isEqualTo(multipart.getMaxRequestSize());
        assertThat(multipart.getMaxFileSize().toBytes()).isGreaterThan(EvidenceFiles.MAX_BYTES);
    }

    private long saveDraft(TSolveUser user, String fields, List<MultipartFile> files) {
        String json = "{\"projectId\":\"" + PROJECT + "\"" + (fields.isEmpty() ? "" : "," + fields) + "}";
        return manualEntries.saveDraft(user, json, files).previewId();
    }

    private String draftEvidence(long draftId) {
        return jdbc.queryForObject("select evidence::text from import_preview where id = ?", String.class, draftId);
    }

    @Test
    void aManualDraftIsListedWithTheOtherDraftsAsManualWithOneRow_AC_007_3() {
        long id = saveDraft(PM, "\"title\":\"Login fails\"", List.of());

        assertThat(drafts.list(PM.getName())).singleElement().satisfies(draft -> {
            assertThat(draft.previewId()).isEqualTo(id);
            assertThat(draft.source()).isEqualTo(TicketSource.MANUAL);
            assertThat(draft.projectId()).isEqualTo(PROJECT);
            assertThat(draft.rowCount()).isEqualTo(1);
            assertThat(draft.savedAt()).isNotNull();
        });
    }

    @Test
    void theDailyCleanupKeepsAManualDraftOlderThanADay_AC_007_1() {
        // edge 27: a Manual Draft is a Draft from its first save (AC-004.6)
        long id = saveDraft(PM, "", List.of());
        jdbc.update("update import_preview set created_at = now() - interval '3 days' where id = ?", id);

        cleanup.deleteExpiredPreviews();

        assertThat(count("import_preview")).isEqualTo(1);
    }

    @Test
    void savingADraftAsATicketDeletesTheDraftInTheSameTransaction_AC_007_6() {
        long id = saveDraft(PM, "", List.of(file("a.png", new byte[] {1}), file("b.log", new byte[] {2})));
        String keepA = jdbc.queryForObject(
                "select e->>'id' from import_preview, jsonb_array_elements(evidence) e where e->>'fileName' = 'a.png'",
                String.class);

        ManualEntryResponse response = manualEntries.save(
                PM,
                entry("T-1").replace("}", ",\"draftId\":" + id + ",\"keepEvidenceIds\":[\"" + keepA + "\"]}"),
                List.of(file("c.pdf", new byte[] {3})));

        assertThat(count("import_preview")).isZero();
        assertThat(count("import_preview_row")).isZero();
        assertThat(jdbc.queryForList(
                        "select file_name from evidence where ticket_id = ? order by file_name",
                        String.class,
                        response.ticketId()))
                .containsExactly("a.png", "c.pdf");
        assertThat(jdbc.queryForList("select storage_key from evidence", String.class))
                .allSatisfy(key -> assertThat(storage.objects).containsKey(key));
        // b.log was removed: its object is gone after the commit
        assertThat(storage.objects).hasSize(2);
    }

    @Test
    void aFailedSaveFromADraftLeavesTheDraftAsItWas_AC_007_6() {
        jdbc.execute("create or replace function fail_for_boom() returns trigger language plpgsql as "
                + "$$ begin raise exception 'forced failure'; end $$");
        jdbc.execute("create trigger fail_for_boom before insert on ticket_import_decision "
                + "for each row when (new.reviewer = '" + BOOM.id() + "') execute function fail_for_boom()");
        long id = saveDraft(BOOM, "\"title\":\"Draft title\"", List.of(file("a.png", new byte[] {1})));
        String before = draftEvidence(id);

        MessageCodeException e = catchThrowableOfType(
                () -> manualEntries.save(
                        BOOM,
                        entry("T-1").replace("}", ",\"draftId\":" + id + ",\"keepEvidenceIds\":[]}"),
                        List.of(file("c.pdf", new byte[] {3}))),
                MessageCodeException.class);

        assertThat(e.getCode()).isEqualTo("MSG33");
        assertThat(draftEvidence(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select title from import_preview_row where preview_id = ?", String.class, id))
                .isEqualTo("Draft title");
        assertThat(count("ticket")).isZero();
        // the Draft's file stays; the new one is gone
        assertThat(storage.objects).hasSize(1);
    }

    @Test
    void deletingAManualDraftDeletesItAndItsFilesAfterTheCommit_AC_007_7() {
        long id = saveDraft(PM, "", List.of(file("a.png", new byte[] {1}), file("b.log", new byte[] {2})));

        drafts.delete(PM.getName(), id);

        assertThat(count("import_preview")).isZero();
        assertThat(storage.objects).isEmpty();
        assertThat(storage.deleted).hasSize(2);
    }

    @Test
    void aSaveWaitsForAnotherRequestOnTheSameDraftThenFindsItsKeptFileGone_AC_007_9() throws Exception {
        // edge 28: tab B holds the Draft and removes e1 while tab A saves it as a Ticket keeping e1
        long id = saveDraft(PM, "", List.of(file("a.png", new byte[] {1})));
        String e1 = jdbc.queryForObject(
                "select e->>'id' from import_preview, jsonb_array_elements(evidence) e", String.class);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection other = jdbc.getDataSource().getConnection()) {
            other.setAutoCommit(false);
            other.createStatement().execute("select id from import_preview where id = " + id + " for update");
            Future<String> save = pool.submit(() -> {
                try {
                    manualEntries.save(
                            PM,
                            entry("T-1").replace("}", ",\"draftId\":" + id + ",\"keepEvidenceIds\":[\"" + e1 + "\"]}"),
                            List.of(file("c.pdf", new byte[] {3})));
                    return "ok";
                } catch (NotFoundException e) {
                    return e.getCode();
                }
            });
            awaitWaitingLock();
            other.createStatement().execute("update import_preview set evidence = '[]' where id = " + id);
            other.commit();

            assertThat(save.get()).isEqualTo("MSG04");
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("ticket")).isZero();
        assertThat(count("evidence")).isZero();
        // only a.png is left: A's new file was deleted (B's after-commit delete is not part of this test)
        assertThat(storage.objects).hasSize(1);
        assertThat(count("import_batch")).isZero();
    }

    /** Polls until some session waits for a lock, so the test does not race the other thread. */
    private void awaitWaitingLock() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_locks where not granted", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the save never waited for the other request's Ticket ID");
    }
}
