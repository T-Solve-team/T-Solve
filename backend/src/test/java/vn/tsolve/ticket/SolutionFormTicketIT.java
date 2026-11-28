package vn.tsolve.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.ticket.dto.IssueStatusChange;
import vn.tsolve.ticket.dto.OpenDraft;
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.dto.TicketReviewView.QueueItem;
import vn.tsolve.ticket.exception.TicketAlreadyExistsException;
import vn.tsolve.ticket.exception.TicketNotResubmittableException;
import vn.tsolve.ticket.service.SolutionDraftService;
import vn.tsolve.ticket.service.SolutionFormTicketService;
import vn.tsolve.ticket.service.TicketReviewReadService;

/**
 * The Solution Form ticket as `ticket-review` sees it: stored in the bound Workspace's database, in the Review Queue
 * with its entry time and due date (jira-app AC-004.1), one per issue under concurrent submits (AC-004.3), and the
 * Draft kept per creator (AC-005.1, AC-005.3). Same properties as the identity ITs, so the Spring context is shared.
 */
@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://127.0.0.1:9",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
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
@AutoConfigureMockMvc
@Import(TwoTenantDatabases.class)
class SolutionFormTicketIT {

    private static final UUID DEPARTMENT = new UUID(126, 101);
    private static final UUID PROJECT = new UUID(126, 301);
    private static final UUID WRITER = new UUID(126, 401);
    private static final UUID OTHER = new UUID(126, 402);
    private static final String KEY = "SOLFORM";
    private static final int REVIEW_RANGE = 5;

    @Autowired
    private SolutionFormTicketService tickets;

    @Autowired
    private SolutionDraftService drafts;

    @Autowired
    private TicketReviewReadService reads;

    @Autowired
    private TenantScope scope;

    private final JdbcTemplate db = TwoTenantDatabases.tenantA();

    @BeforeEach
    void seed() {
        db.update("DELETE FROM solution_draft WHERE project_id = ?", PROJECT);
        db.update("DELETE FROM jira_issue_done_state WHERE project_id = ?", PROJECT);
        db.update("DELETE FROM ticket WHERE project_id = ?", PROJECT);
        db.update("DELETE FROM app_user WHERE id IN (?,?)", WRITER, OTHER);
        db.update("DELETE FROM project WHERE public_id = ?", PROJECT);
        db.update("DELETE FROM department WHERE public_id = ?", DEPARTMENT);
        db.update("INSERT INTO department(public_id,name,status) VALUES (?,'Solution Form IT','ACTIVE')", DEPARTMENT);
        db.update(
                "INSERT INTO project(public_id,department_id,jira_project_key,status,review_range) SELECT"
                        + " ?,department_id,?,'ACTIVE',? FROM department WHERE public_id = ?",
                PROJECT,
                KEY,
                REVIEW_RANGE,
                DEPARTMENT);
        account(WRITER, "Ngo Bao");
        account(OTHER, "Tran Lan");
    }

    @Test
    void entersTheReviewQueueWithEntryTimeAndDueDate_AC_004_1() {
        String issue = KEY + "-1";
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            drafts.save(WRITER, PROJECT, issue, new SolutionSections("Draft", "", "", "", ""));
            return tickets.submit(submission(issue, WRITER));
        });

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        List<QueueItem> queue = scope.runIn(TwoTenantDatabases.ACME, () -> reads.queue(
                        Map.of(PROJECT, new ProjectAccess(new Project(PROJECT, KEY), DEPARTMENT, true)),
                        null,
                        PageRequest.of(0, 20),
                        List.of())
                .getContent());
        assertThat(queue).singleElement().satisfies(item -> {
            assertThat(item.sourceTicketId()).isEqualTo(issue);
            assertThat(item.title()).isEqualTo("VPN drops after sleep");
            assertThat(item.source()).isEqualTo("SOLUTION_FORM");
            assertThat(item.authorName()).isEqualTo("Ngo Bao");
            assertThat(item.enteredQueueAt()).isNotNull();
            assertThat(item.reviewDueDate().toLocalDate()).isEqualTo(today.plusDays(REVIEW_RANGE));
        });
        assertThat(db.queryForObject("SELECT writer_id FROM ticket WHERE source_ticket_id = ?", UUID.class, issue))
                .isEqualTo(WRITER);
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(WRITER, PROJECT, issue)))
                .isEmpty();
        assertThat(TwoTenantDatabases.tenantB()
                        .queryForObject("SELECT count(*) FROM ticket WHERE source_ticket_id = ?", Integer.class, issue))
                .isZero();
    }

    @Test
    void createsOneTicketWhenTwoSubmitsRace_AC_004_3() throws Exception {
        String issue = KEY + "-2";
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> submit = () -> {
            start.await();
            try {
                scope.runIn(TwoTenantDatabases.ACME, () -> tickets.submit(submission(issue, WRITER)));
                return "created";
            } catch (TicketAlreadyExistsException e) {
                return "409";
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            results.add(pool.submit(submit));
            results.add(pool.submit(submit));
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get());
            }
            assertThat(outcomes).containsExactlyInAnyOrder("created", "409");
        } finally {
            pool.shutdownNow();
        }
        assertThat(db.queryForObject("SELECT count(*) FROM ticket WHERE source_ticket_id = ?", Integer.class, issue))
                .isEqualTo(1);
    }

    @Test
    void keepsOneDraftPerCreatorAndIssue_AC_005_1_AC_005_3() {
        String issue = KEY + "-3";
        SolutionSections first = new SolutionSections("First", "", "", "", "");
        SolutionSections second = new SolutionSections("Second 😀", "Cause", "", "Restart", "");

        scope.runIn(TwoTenantDatabases.ACME, () -> {
            drafts.save(WRITER, PROJECT, issue, first);
            drafts.save(WRITER, PROJECT, issue, second);
            return null;
        });

        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(WRITER, PROJECT, issue)))
                .contains(second);
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(OTHER, PROJECT, issue)))
                .isEmpty();
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM solution_draft WHERE project_id = ?", Integer.class, PROJECT))
                .isEqualTo(1);
    }

    @Test
    void replacesTheCreatedDraftWithTheAiDraftOnlyUntilItIsSaved_AC_005_11_AC_005_12() {
        String issue = KEY + "-6";
        SolutionSections issueText = new SolutionSections("From the issue", "", "", "", "");
        SolutionSections ai = new SolutionSections("", "Old client", "Update", "", "");
        SolutionSections typed = new SolutionSections("Typed", "", "", "", "");

        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> {
                    drafts.recordIssueStatus(new IssueStatusChange(PROJECT, issue, true, WRITER, issueText));
                    return drafts.replaceUnsavedDraft(WRITER, PROJECT, issue, ai);
                }))
                .isTrue();
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(WRITER, PROJECT, issue)))
                .contains(ai);

        String saved = KEY + "-7";
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> {
                    drafts.recordIssueStatus(new IssueStatusChange(PROJECT, saved, true, WRITER, issueText));
                    drafts.save(WRITER, PROJECT, saved, typed);
                    return drafts.replaceUnsavedDraft(WRITER, PROJECT, saved, ai);
                }))
                .isFalse();
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(WRITER, PROJECT, saved)))
                .contains(typed);
    }

    @Test
    void recordsTheDoneStateAndCreatesTheDraftWithoutReplacingOne_AC_005_7_AC_005_8_AC_005_9() {
        String issue = KEY + "-5";
        SolutionSections closed = new SolutionSections("Typed before", "", "", "", "");
        SolutionSections issueText = new SolutionSections("From the issue", "", "", "", "");

        scope.runIn(TwoTenantDatabases.ACME, () -> {
            drafts.save(WRITER, PROJECT, issue, closed);
            drafts.recordIssueStatus(new IssueStatusChange(PROJECT, issue, true, WRITER, issueText));
            drafts.recordIssueStatus(new IssueStatusChange(PROJECT, issue, true, OTHER, issueText));
            return null;
        });
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(WRITER, PROJECT, issue)))
                .contains(closed);
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(OTHER, PROJECT, issue)))
                .contains(issueText);
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.findIssueDone(PROJECT, issue)))
                .contains(true);

        scope.runIn(TwoTenantDatabases.ACME, () -> {
            drafts.recordIssueStatus(new IssueStatusChange(PROJECT, issue, false, null, null));
            return null;
        });
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.findIssueDone(PROJECT, issue)))
                .contains(false);
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(OTHER, PROJECT, issue)))
                .contains(issueText);
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.findIssueDone(PROJECT, KEY + "-404")))
                .isEmpty();
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM jira_issue_done_state WHERE project_id = ?", Integer.class, PROJECT))
                .isEqualTo(1);
    }

    @Test
    void listsOpenDraftsNewestFirstAndLeavesOutIssuesWithATicketOrNotDone_AC_011_2_AC_011_3() {
        String older = KEY + "-7";
        String newer = KEY + "-8";
        String submitted = KEY + "-9";
        String reopened = KEY + "-10";
        String neverRecorded = KEY + "-11";
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            drafts.save(WRITER, PROJECT, older, new SolutionSections("Older", "", "", "", ""));
            drafts.save(OTHER, PROJECT, newer, new SolutionSections("Newer", "", "", "", ""));
            drafts.save(WRITER, PROJECT, submitted, new SolutionSections("Kept", "", "", "", ""));
            drafts.save(WRITER, PROJECT, reopened, new SolutionSections("Reopened", "", "", "", ""));
            drafts.save(WRITER, PROJECT, neverRecorded, new SolutionSections("Before KAN-371", "", "", "", ""));
            for (String issue : List.of(older, newer, submitted, reopened)) {
                drafts.recordIssueStatus(new IssueStatusChange(PROJECT, issue, true, null, null));
            }
            // Moved out of Done after the Draft: no reminder while it is not Done (AC-011.2, AC-008.11).
            drafts.recordIssueStatus(new IssueStatusChange(PROJECT, reopened, false, null, null));
            return null;
        });
        db.update("UPDATE solution_draft SET updated_at = now() - interval '2 days' WHERE jira_issue_key = ?", older);
        // A ticket of any source for the issue closes its Draft ("the issue's ticket").
        db.update(
                "INSERT INTO ticket(source_ticket_id,project_id,source,status,title,writer,symptoms,root_cause,"
                        + "resolution_steps,scope) VALUES (?,?,'CSV','PUBLISHED','Imported','Ngo Bao','s','r','f','a')",
                submitted,
                PROJECT);

        // Tenant A is shared per JVM, so keep only this test's issues.
        List<OpenDraft> all = scope.runIn(TwoTenantDatabases.ACME, () -> drafts.findOpenDrafts()).stream()
                .filter(draft -> draft.issueKey().startsWith(KEY + "-"))
                .toList();
        List<OpenDraft> mine = scope.runIn(TwoTenantDatabases.ACME, () -> drafts.findOpenDrafts(WRITER));

        assertThat(all).extracting(OpenDraft::issueKey).containsExactly(newer, older);
        assertThat(all).extracting(OpenDraft::creatorId).containsExactly(OTHER, WRITER);
        assertThat(mine).extracting(OpenDraft::issueKey).containsExactly(older);
        assertThat(mine.get(0).updatedAt()).isNotNull();
        assertThat(TwoTenantDatabases.tenantB()
                        .queryForObject(
                                "SELECT count(*) FROM solution_draft WHERE project_id = ?", Integer.class, PROJECT))
                .isZero();
    }

    @Test
    void resubmitsTheWritersTicketInChangesRequestedOnce_AC_012_2_AC_012_4_AC_012_5() {
        String issue = KEY + "-4";
        scope.runIn(TwoTenantDatabases.ACME, () -> tickets.submit(submission(issue, WRITER)));
        db.update(
                "UPDATE ticket SET status = 'CHANGES_REQUESTED', change_request_comment = 'Add the client version',"
                        + " review_due_date = DATE '2026-01-04', review_queue_entered_at = TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE source_ticket_id = ?",
                issue);
        SolutionSections fixed = new SolutionSections("Drops hourly", "Old client", "Update to 5.3", "", "All laptops");
        SolutionFormSubmission byWriter = new SolutionFormSubmission(PROJECT, issue, "VPN", WRITER, "Ngo Bao", fixed);
        SolutionFormSubmission byOther = new SolutionFormSubmission(PROJECT, issue, "VPN", OTHER, "Tran Lan", fixed);

        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> tickets.findResubmittable(issue, OTHER)))
                .isEmpty();
        assertThatThrownBy(() -> scope.runIn(TwoTenantDatabases.ACME, () -> tickets.resubmit(byOther)))
                .isInstanceOf(TicketNotResubmittableException.class);
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> tickets.findResubmittable(issue, WRITER)))
                .contains(new SolutionSections("Drops hourly", "Old client", "Update to 5.2", "", "Windows laptops"));

        scope.runIn(TwoTenantDatabases.ACME, () -> {
            drafts.save(WRITER, PROJECT, issue, fixed);
            return tickets.resubmit(byWriter);
        });

        Map<String, Object> row = db.queryForMap(
                "SELECT status, resolution_steps, scope, review_due_date, review_queue_entered_at, title,"
                        + " change_request_comment FROM ticket WHERE source_ticket_id = ?",
                issue);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(row.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(row.get("resolution_steps")).isEqualTo("Update to 5.3");
        assertThat(row.get("scope")).isEqualTo("All laptops");
        assertThat(row.get("title")).isEqualTo("VPN drops after sleep");
        assertThat(row.get("change_request_comment")).isNull();
        assertThat(((java.sql.Date) row.get("review_due_date")).toLocalDate()).isEqualTo(today.plusDays(REVIEW_RANGE));
        assertThat(((java.sql.Timestamp) row.get("review_queue_entered_at")).toInstant())
                .isAfter(java.time.Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> drafts.find(WRITER, PROJECT, issue)))
                .isEmpty();
        assertThatThrownBy(() -> scope.runIn(TwoTenantDatabases.ACME, () -> tickets.resubmit(byWriter)))
                .isInstanceOf(TicketNotResubmittableException.class);
    }

    private void account(UUID id, String name) {
        db.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at) VALUES (?,?,?,'ACTIVE',now())",
                id,
                id + "@solution-form-fixture.test",
                name);
    }

    private static SolutionFormSubmission submission(String issue, UUID writer) {
        return new SolutionFormSubmission(
                PROJECT,
                issue,
                "VPN drops after sleep",
                writer,
                "Ngo Bao",
                new SolutionSections("Drops hourly", "Old client", "Update to 5.2", "", "Windows laptops"));
    }
}
