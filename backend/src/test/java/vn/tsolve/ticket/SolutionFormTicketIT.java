package vn.tsolve.ticket;

import static org.assertj.core.api.Assertions.assertThat;

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
import vn.tsolve.ticket.dto.SolutionFormSubmission;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.dto.TicketReviewView.QueueItem;
import vn.tsolve.ticket.exception.TicketAlreadyExistsException;
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
