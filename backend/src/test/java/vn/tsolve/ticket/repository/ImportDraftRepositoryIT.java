package vn.tsolve.ticket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.testsupport.SharedPostgres;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.IssueComment;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;

/** Drafts and Jira rows against a real PostgreSQL: the partial unique index, Draft-aware deletes, JSONB comments. */
@SpringBootTest
@TestPropertySource(
        properties = {
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
class ImportDraftRepositoryIT {

    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private static final Instant TWO_DAYS_AGO = NOW.minusSeconds(48 * 3600);

    @Autowired
    private ImportPreviewRepository previews;

    @Autowired
    private ImportPreviewRowRepository rows;

    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void clean() {
        previews.deleteAll();
    }

    private ImportPreview jira(String owner, Instant createdAt) {
        return previews.save(ImportPreview.jira(
                owner, PROJECT, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), false, createdAt));
    }

    private ImportPreview draft(String owner, Instant createdAt, Instant savedAt) {
        ImportPreview preview = jira(owner, createdAt);
        preview.saveAsDraft(savedAt);
        return previews.save(preview);
    }

    @Test
    void cleanupSkipsDraftsOlderThanTwentyFourHours_AC_004_6() {
        ImportPreview abandoned = jira("pm-a", TWO_DAYS_AGO);
        draft("pm-b", TWO_DAYS_AGO, TWO_DAYS_AGO);

        assertThat(previews.findIdsCreatedBefore(NOW.minusSeconds(24 * 3600))).containsExactly(abandoned.getId());
    }

    @Test
    void newPullOrUploadKeepsTheCallersDrafts_AC_004_6() {
        ImportPreview kept = draft("pm-a", NOW, NOW);
        ImportPreview open = jira("pm-a", NOW);

        transaction.executeWithoutResult(status -> previews.deleteByOwner("pm-a"));

        assertThat(previews.findById(open.getId())).isEmpty();
        assertThat(previews.findById(kept.getId())).isPresent();
    }

    @Test
    void anOwnerHasOneOpenPreviewBesideAnyNumberOfDrafts() {
        draft("pm-a", NOW, NOW);
        draft("pm-a", NOW, NOW.plusSeconds(60));
        jira("pm-a", NOW);

        assertThatThrownBy(() -> jira("pm-a", NOW)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsTheOwnersDraftsNewestFirst() {
        ImportPreview older = draft("pm-a", NOW, NOW);
        ImportPreview newer = draft("pm-a", NOW, NOW.plusSeconds(60));
        draft("pm-b", NOW, NOW.plusSeconds(120));
        jira("pm-a", NOW);

        assertThat(previews.findByOwnerAndDraftSavedAtIsNotNullOrderByDraftSavedAtDesc("pm-a"))
                .extracting(ImportPreview::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void savingADraftAgainKeepsTheSameDraft() {
        ImportPreview preview = draft("pm-a", NOW, NOW);
        preview.saveAsDraft(NOW.plusSeconds(60));
        previews.save(preview);

        ImportPreview read = previews.findById(preview.getId()).orElseThrow();
        assertThat(read.getDraftSavedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(previews.count()).isEqualTo(1);
    }

    @Test
    void storesAJiraPreviewWithoutAFileAndItsRowsWithLinkAndComments() {
        ImportPreview preview = jira("pm-a", NOW);
        List<IssueComment> comments = List.of(
                new IssueComment("Hà", Instant.parse("2026-10-02T01:00:00Z"), "Restarted the gateway."),
                new IssueComment(null, Instant.parse("2026-10-02T02:00:00Z"), "Fixed."));
        rows.save(ImportPreviewRow.jiraIssue(
                preview.getId(),
                1,
                new ImportPreviewRow.Values(
                        "KAN-7", "Login fails", "Users see 500", "Hà", "2026-10-02", null, null, null, null, null),
                "https://example.atlassian.net/browse/KAN-7",
                comments));

        ImportPreview readPreview = previews.findById(preview.getId()).orElseThrow();
        assertThat(readPreview.getSource()).isEqualTo(TicketSource.JIRA_IMPORT);
        assertThat(readPreview.getFileName()).isNull();
        assertThat(readPreview.getResolvedFrom()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(readPreview.getResolvedTo()).isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(readPreview.isDraft()).isFalse();

        ImportPreviewRow row =
                rows.findById(new ImportPreviewRow.Id(preview.getId(), 1)).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PreviewRowStatus.NEEDS_SOLUTION);
        assertThat(row.getSourceUrl()).isEqualTo("https://example.atlassian.net/browse/KAN-7");
        assertThat(row.getComments()).isEqualTo(comments);

        previews.deleteById(preview.getId());
        assertThat(rows.findById(new ImportPreviewRow.Id(preview.getId(), 1))).isEmpty();
    }
}
