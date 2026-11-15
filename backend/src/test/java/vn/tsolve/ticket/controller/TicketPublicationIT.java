package vn.tsolve.ticket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;

/** Unpublish / re-publish over HTTP with the real database and the real Project scope (STORY-009, KAN-136). */
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
@Import(TwoTenantDatabases.class)
@RecordApplicationEvents
@WithTSolveUser(id = TicketPublicationIT.MANAGER, roles = Role.PROJECT_MANAGER)
class TicketPublicationIT {
    static final String MANAGER = "00000000-0136-4000-8000-000000000001";
    private static final UUID MANAGER_ID = UUID.fromString(MANAGER);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ApplicationEvents events;

    private final JdbcTemplate db = TwoTenantDatabases.tenantA();
    private UUID project;
    private UUID otherProject;

    @BeforeAll
    static void account() {
        TwoTenantDatabases.tenantA()
                .update(
                        "insert into app_user(id, email, full_name, status, created_at) values (?, ?, 'Publication PM', 'ACTIVE', now())",
                        MANAGER_ID,
                        MANAGER + "@example.test");
    }

    @BeforeEach
    void projects() {
        Long department = db.queryForObject(
                "insert into department(name) values (?) returning department_id",
                Long.class,
                "Publication " + UUID.randomUUID());
        project = project(department);
        otherProject = project(department);
        db.update(
                "insert into project_membership(user_id, project_id, role_in_project) values (?, ?, 'PROJECT_MANAGER')",
                MANAGER_ID,
                project);
    }

    @Test
    void unpublishThenRepublishStoresDecisionsAndAudit_AC_009_1_AC_009_2_AC_010_4() throws Exception {
        UUID first = ticket(project, "PUBLISHED");
        UUID second = ticket(project, "PUBLISHED");
        db.update(
                "insert into evidence (ticket_id, file_name, file_type, file_size) values (?, 'error.png', 'image/png', 3)",
                first);
        Map<String, Object> before = kept(first);

        call("unpublish", body(List.of(first, second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        for (UUID id : List.of(first, second)) {
            assertThat(statusOf(id)).isEqualTo("UNPUBLISHED");
            assertThat(decisions(id)).containsExactly("UNPUBLISH");
            assertThat(audits(id)).containsExactly("UNPUBLISH");
        }

        call("republish", body(List.of(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        assertThat(statusOf(first)).isEqualTo("PUBLISHED");
        assertThat(statusOf(second)).isEqualTo("UNPUBLISHED");
        assertThat(decisions(first)).containsExactly("UNPUBLISH", "REPUBLISH");
        assertThat(audits(first)).containsExactlyInAnyOrder("UNPUBLISH", "PUBLISH");
        assertThat(kept(first)).isEqualTo(before);
        assertThat(db.queryForMap(
                        "select reviewer_id, feedback from ticket_decision where ticket_id = ? and decision = 'REPUBLISH'",
                        first))
                .containsEntry("reviewer_id", MANAGER_ID)
                .containsEntry("feedback", null);
        // No writer email for these decisions (design P9).
        assertThat(events.stream(ReviewFeedbackDecidedEvent.class)).isEmpty();
    }

    @Test
    void aSecondUnpublishIsAConflictAndChangesNothing_AC_009_3() throws Exception {
        UUID id = ticket(project, "PUBLISHED");
        call("unpublish", body(List.of(id))).andExpect(status().isOk());

        call("unpublish", body(List.of(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG06"));
        UUID pending = ticket(project, "PENDING_REVIEW");
        call("republish", body(List.of(id, pending))).andExpect(status().isConflict());

        assertThat(statusOf(id)).isEqualTo("UNPUBLISHED");
        assertThat(statusOf(pending)).isEqualTo("PENDING_REVIEW");
        assertThat(decisions(id)).containsExactly("UNPUBLISH");
        assertThat(decisions(pending)).isEmpty();
    }

    @Test
    void aTicketOfAnotherWorkspaceIsNotFound_AC_009_4() throws Exception {
        UUID mine = ticket(project, "PUBLISHED");

        call("unpublish", body(List.of(mine, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));

        assertThat(statusOf(mine)).isEqualTo("PUBLISHED");
    }

    @Test
    void anotherProjectAndARemovedProjectAreForbidden_AC_009_5() throws Exception {
        UUID mine = ticket(project, "PUBLISHED");
        UUID theirs = ticket(otherProject, "PUBLISHED");

        call("unpublish", body(List.of(mine, theirs)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        assertThat(statusOf(mine)).isEqualTo("PUBLISHED");

        // D37: a Project that is no longer Active is not managed, as for every review decision.
        db.update("update project set status = 'REMOVED' where public_id = ?", project);
        call("unpublish", body(List.of(mine)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        assertThat(statusOf(mine)).isEqualTo("PUBLISHED");
        assertThat(decisions(mine)).isEmpty();
    }

    @Test
    @WithTSolveUser(id = MANAGER, roles = Role.TICKET_RESOLVER)
    void aTicketResolverIsForbidden_AC_009_6() throws Exception {
        UUID id = ticket(project, "PUBLISHED");

        call("unpublish", body(List.of(id))).andExpect(status().isForbidden());
        call("republish", body(List.of(id))).andExpect(status().isForbidden());

        assertThat(statusOf(id)).isEqualTo("PUBLISHED");
    }

    @Test
    void anEmptyOrOversizedBodyIsABadRequest_AC_009_7() throws Exception {
        List<UUID> oversized = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            oversized.add(UUID.randomUUID());
        }
        for (String action : List.of("unpublish", "republish")) {
            call(action, "{\"ticketIds\":[]}").andExpect(status().isBadRequest());
            call(action, body(oversized)).andExpect(status().isBadRequest());
        }
    }

    private Map<String, Object> kept(UUID id) {
        Map<String, Object> ticket = db.queryForMap(
                "select symptoms, root_cause, resolution_steps, workaround, scope, rejection_reason, change_request_comment, review_due_date from ticket where ticket_id = ?",
                id);
        ticket.put("evidence", db.queryForList("select * from evidence where ticket_id = ?", id));
        return ticket;
    }

    private String statusOf(UUID id) {
        return db.queryForObject("select status from ticket where ticket_id = ?", String.class, id);
    }

    private List<String> decisions(UUID id) {
        return db.queryForList(
                "select decision from ticket_decision where ticket_id = ? order by decided_at", String.class, id);
    }

    private List<String> audits(UUID id) {
        return db.queryForList(
                "select action from audit_log_entry where target_type = 'TICKET' and target_id = ? and actor_id = ?",
                String.class,
                id,
                MANAGER_ID);
    }

    private UUID project(Long department) {
        UUID id = UUID.randomUUID();
        db.update(
                "insert into project(department_id, jira_project_key, public_id) values (?, ?, ?)",
                department,
                "P" + id.toString().substring(0, 12),
                id);
        return id;
    }

    private UUID ticket(UUID projectId, String status) {
        return db.queryForObject("""
                insert into ticket(source_ticket_id, project_id, source, status, title, symptoms, root_cause,
                                   resolution_steps, workaround, scope, rejection_reason, change_request_comment,
                                   review_due_date)
                values (?, ?, 'SOLUTION_FORM', ?, 'Login restored', 'Login failed', 'Token', 'Refresh token', 'Retry',
                        'Users', 'Old reason', 'Old comment', '2026-11-01')
                returning ticket_id
                """, UUID.class, UUID.randomUUID().toString(), projectId, status);
    }

    private ResultActions call(String action, String body) {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(post("/api/v1/reviews/" + action)
                        .with(csrf())
                        .contentType("application/json")
                        .content(body));
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
    }

    private static String body(List<UUID> ids) {
        return "{\"ticketIds\":[" + ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }
}
