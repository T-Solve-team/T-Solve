package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewWorkspaceAccess;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.worker.service.TenantJobService;

/** Production Expired tickets (STORY-013). */
@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://login.localhost:8081",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0"
        })
@AutoConfigureMockMvc
@Import({TwoTenantDatabases.class, SendBackApiIT.WorkspaceAccessConfiguration.class})
@WithTSolveUser(id = SendBackApiIT.MANAGER_ID, roles = Role.PROJECT_MANAGER)
class SendBackApiIT {

    static final String MANAGER_ID = "95b6e3be-105e-4c2d-b875-145891095340";
    private static final UUID MANAGER = UUID.fromString(MANAGER_ID);
    private static final UUID LONG_RANGE = new UUID(0, 915);
    private static final UUID ZERO = new UUID(0, 913);
    private static final UUID NEGATIVE = new UUID(0, 914);
    private static final UUID OPS = new UUID(0, 910);
    private static final UUID SUPPORT = new UUID(0, 911);
    private static final UUID UNMANAGED = new UUID(0, 912);
    private static final UUID OPS_DEPARTMENT = new UUID(0, 810);
    private static final UUID SUPPORT_DEPARTMENT = new UUID(0, 811);
    private static final UUID OTHER_DEPARTMENT = new UUID(0, 812);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope workspace;

    private static final Instant NOW = Instant.parse("2026-11-08T23:30:00Z");

    @MockBean
    private TenantJobService jobs;

    @TestConfiguration(proxyBeanMethods = false)
    static class WorkspaceAccessConfiguration {

        @Bean
        @Primary
        Clock sendBackClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ReviewWorkspaceAccess reviewWorkspaceAccess(TwoTenantDatabases.RoutingTenantScope scope) {
            return new ReviewWorkspaceAccess() {
                @Override
                public Optional<Set<UUID>> managedProjectIds(UUID accountId) {
                    return readableProjects(accountId)
                            .map(projects -> Set.of(OPS, SUPPORT, ZERO, NEGATIVE, LONG_RANGE));
                }

                @Override
                public Optional<Map<UUID, ProjectAccess>> readableProjects(UUID accountId) {
                    try {
                        if (!scope.currentWorkspaceId().equals(TwoTenantDatabases.ACME) || !accountId.equals(MANAGER)) {
                            return Optional.empty();
                        }
                    } catch (IllegalStateException unbound) {
                        return Optional.empty();
                    }
                    return Optional.of(Map.of(
                            LONG_RANGE, new ProjectAccess(new Project(LONG_RANGE, "Long"), OPS_DEPARTMENT, true),
                            ZERO, new ProjectAccess(new Project(ZERO, "Zero"), OPS_DEPARTMENT, true),
                            NEGATIVE, new ProjectAccess(new Project(NEGATIVE, "Negative"), OPS_DEPARTMENT, true),
                            OPS, new ProjectAccess(new Project(OPS, "Operations"), OPS_DEPARTMENT, true),
                            SUPPORT, new ProjectAccess(new Project(SUPPORT, "Support"), SUPPORT_DEPARTMENT, true),
                            UNMANAGED, new ProjectAccess(new Project(UNMANAGED, "Finance"), OTHER_DEPARTMENT, false)));
                }
            };
        }
    }

    @BeforeAll
    static void seedProjectsAndReviewer() {
        var db = TwoTenantDatabases.tenantA();
        db.update(
                "insert into app_user (id, email, status, created_at) values (?, 'sendback-pm@example.test', 'ACTIVE', now())",
                MANAGER);
        Long department = db.queryForObject(
                "insert into department (name) values ('Send back tests') returning department_id", Long.class);
        for (UUID project : List.of(OPS, SUPPORT, UNMANAGED, ZERO, NEGATIVE, LONG_RANGE)) {
            Integer range = null;
            if (project.equals(OPS)) range = 5;
            else if (project.equals(LONG_RANGE)) range = 7;
            else if (project.equals(ZERO) || project.equals(UNMANAGED)) range = 0;
            else if (project.equals(NEGATIVE)) range = -1;
            db.update(
                    "insert into project (public_id, department_id, jira_project_key, review_range) values (?, ?, ?, ?)",
                    project,
                    department,
                    "SB-" + project.toString().substring(30),
                    range);
            if (!project.equals(UNMANAGED)) {
                db.update(
                        "insert into project_membership (user_id, project_id, role_in_project) values (?, ?, 'PROJECT_MANAGER')",
                        MANAGER,
                        project);
            }
        }
    }

    @BeforeEach
    void resetNotifications() {
        clearInvocations(jobs);
    }

    @Test
    void sendsBackAtomicallyWithVietnamDatesAndPreservesContent_AC_013_5() throws Exception {
        var db = TwoTenantDatabases.tenantA();
        UUID first = ticket(db, OPS, "EXPIRED", "SOLUTION_FORM");
        UUID second = ticket(db, SUPPORT, "EXPIRED", "SOLUTION_FORM");
        UUID longer = ticket(db, LONG_RANGE, "EXPIRED", "SOLUTION_FORM");
        UUID zero = ticket(db, ZERO, "EXPIRED", "SOLUTION_FORM");
        UUID negative = ticket(db, NEGATIVE, "EXPIRED", "SOLUTION_FORM");
        db.update(
                "insert into evidence (ticket_id, file_name, file_type, file_size) values (?, 'error.png', 'image/png', 3)",
                first);
        var evidenceBefore = db.queryForList("select * from evidence where ticket_id = ?", first);
        var contentBefore = db.queryForMap(
                "select symptoms, root_cause, resolution_steps, workaround, scope, rejection_reason, change_request_comment from ticket where ticket_id = ?",
                first);
        Instant before = db.queryForObject("select statement_timestamp()", OffsetDateTime.class)
                .toInstant();
        inAcme(request(body(List.of(first, second, zero, negative, longer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
        Instant after = db.queryForObject("select statement_timestamp()", OffsetDateTime.class)
                .toInstant();
        for (UUID id : List.of(first, second, zero, negative, longer)) {
            var ticket = db.queryForMap(
                    "select status, review_due_date, review_queue_entered_at, updated_at from ticket where ticket_id = ?",
                    id);
            assertThat(ticket).containsEntry("status", "PENDING_REVIEW");
            assertThat(db.queryForObject("select review_due_date from ticket where ticket_id = ?", LocalDate.class, id))
                    .isEqualTo(LocalDate.of(2026, 11, id.equals(first) ? 14 : id.equals(longer) ? 16 : 12));
            assertThat(db.queryForObject(
                                    "select review_queue_entered_at from ticket where ticket_id = ?",
                                    OffsetDateTime.class,
                                    id)
                            .toInstant())
                    .isBetween(before, after);
            assertThat(db.queryForObject("select updated_at from ticket where ticket_id = ?", OffsetDateTime.class, id)
                            .toInstant())
                    .isBetween(before, after);
            assertThat(db.queryForObject("select count(*) from review_decision where ticket_id = ?", Integer.class, id))
                    .isEqualTo(1);
            assertThat(db.queryForMap(
                            "select decision, project_manager_id, reason_or_comment from review_decision where ticket_id = ?",
                            id))
                    .containsEntry("decision", "SEND_BACK")
                    .containsEntry("project_manager_id", MANAGER)
                    .containsEntry("reason_or_comment", null);
            assertThat(db.queryForObject(
                                    "select decided_at from review_decision where ticket_id = ?",
                                    OffsetDateTime.class,
                                    id)
                            .toInstant())
                    .isEqualTo(NOW);
        }
        assertThat(db.queryForMap(
                        "select symptoms, root_cause, resolution_steps, workaround, scope, rejection_reason, change_request_comment from ticket where ticket_id = ?",
                        first))
                .isEqualTo(contentBefore);
        assertThat(db.queryForList("select * from evidence where ticket_id = ?", first))
                .isEqualTo(evidenceBefore);
        verifyNoInteractions(jobs);
    }

    @Test
    void rejectsInvalidShapesWithoutChangingTickets_AC_013_6() throws Exception {
        var db = TwoTenantDatabases.tenantA();
        UUID id = ticket(db, OPS, "EXPIRED", "SOLUTION_FORM");
        var before = db.queryForMap("select * from ticket where ticket_id = ?", id);
        var oversized = new java.util.ArrayList<UUID>();
        oversized.add(id);
        for (int i = 1; i < 501; i++) oversized.add(UUID.randomUUID());
        for (String json : List.of(
                "{\"ticketIds\":[]}",
                body(oversized),
                body(List.of(id, id)),
                "{\"ticketIds\":[\"" + id + "\",\"invalid\"]}")) {
            inAcme(request(json)).andExpect(status().isBadRequest());
            assertUnchanged(db, id, before);
        }
    }

    @Test
    void rejectsMissingUnmanagedAndStaleBatchesInOrder_AC_013_6() throws Exception {
        var db = TwoTenantDatabases.tenantA();
        UUID eligible = ticket(db, OPS, "EXPIRED", "SOLUTION_FORM");
        UUID unmanaged = ticket(db, UNMANAGED, "EXPIRED", "SOLUTION_FORM");
        UUID stale = ticket(db, OPS, "PENDING_REVIEW", "SOLUTION_FORM");
        UUID imported = ticket(db, OPS, "EXPIRED", "CSV");
        UUID foreign = ticket(TwoTenantDatabases.tenantB(), OPS, "EXPIRED", "SOLUTION_FORM");
        var snapshots = new java.util.HashMap<UUID, Map<String, Object>>();
        for (UUID id : List.of(eligible, unmanaged, stale, imported))
            snapshots.put(id, db.queryForMap("select * from ticket where ticket_id = ?", id));
        var cases = Map.of(UUID.randomUUID(), 404, foreign, 404, unmanaged, 403, stale, 409, imported, 409);
        for (var entry : cases.entrySet()) {
            inAcme(request(body(List.of(eligible, entry.getKey())))).andExpect(status().is(entry.getValue()));
            for (var snapshot : snapshots.entrySet()) assertUnchanged(db, snapshot.getKey(), snapshot.getValue());
        }
        inAcme(request(body(List.of(eligible, unmanaged, stale, UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        inAcme(request(body(List.of(eligible, unmanaged, stale)))).andExpect(status().isForbidden());
        for (var snapshot : snapshots.entrySet()) assertUnchanged(db, snapshot.getKey(), snapshot.getValue());
        assertThat(TwoTenantDatabases.tenantB()
                        .queryForObject("select status from ticket where ticket_id = ?", String.class, foreign))
                .isEqualTo("EXPIRED");
    }

    @Test
    @WithAnonymousUser
    void deniesAnonymousSendBack_AC_013_7() throws Exception {
        inAcme(request(body(List.of(UUID.randomUUID())))).andExpect(status().isUnauthorized());
    }

    @Test
    @WithTSolveUser(id = MANAGER_ID, roles = Role.EMPLOYEE)
    void deniesNonManagerSendBack_AC_013_7() throws Exception {
        inAcme(request(body(List.of(UUID.randomUUID())))).andExpect(status().isForbidden());
    }

    @Test
    @WithTSolveUser(id = "33333333-3333-4333-8333-333333333333", roles = Role.PROJECT_MANAGER)
    void deniesUnverifiedAccountSendBack_AC_013_7() throws Exception {
        inAcme(request(body(List.of(UUID.randomUUID())))).andExpect(status().isForbidden());
    }

    @Test
    void deniesUnboundAndOtherWorkspaceSendBack_AC_013_7() throws Exception {
        String json = body(List.of(UUID.randomUUID()));
        mvc.perform(request(json)).andExpect(status().isForbidden());
        workspace.runIn(TwoTenantDatabases.BETA, () -> {
            try {
                return mvc.perform(request(json)).andExpect(status().isForbidden());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private void assertUnchanged(JdbcTemplate db, UUID id, Map<String, Object> before) {
        assertThat(db.queryForMap("select * from ticket where ticket_id = ?", id))
                .isEqualTo(before);
        assertThat(db.queryForObject("select count(*) from review_decision where ticket_id = ?", Integer.class, id))
                .isZero();
        verifyNoInteractions(jobs);
    }

    private ResultActions inAcme(RequestBuilder request) {
        return workspace.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(request);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static RequestBuilder request(String body) {
        return post("/api/v1/reviews/send-back")
                .with(csrf())
                .contentType("application/json")
                .content(body);
    }

    private static String body(List<UUID> ids) {
        return "{\"ticketIds\":["
                + ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    private static UUID ticket(JdbcTemplate db, UUID project, String status, String source) {
        return db.queryForObject(
                """
                insert into ticket (source_ticket_id, project_id, source, status, title, symptoms, root_cause,
                                    resolution_steps, workaround, scope, rejection_reason, change_request_comment, review_due_date)
                values (?, ?, ?, ?, 'Login fails', 'Error', 'Token', 'Restart', 'Retry', 'Users', 'Old reason', 'Old comment', '2026-11-01') returning ticket_id
                """,
                UUID.class,
                UUID.randomUUID().toString(),
                project,
                source,
                status);
    }
}
