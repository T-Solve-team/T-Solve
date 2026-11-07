package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
@Import({TwoTenantDatabases.class, ReviewDecisionApiIT.WorkspaceAccessConfiguration.class})
@WithTSolveUser(id = ReviewDecisionApiIT.MANAGER_ID, roles = Role.PROJECT_MANAGER)
class ReviewDecisionApiIT {

    static final String MANAGER_ID = "f4b304e6-d813-4e47-9b6c-69b7d61cc6cb";
    private static final UUID MANAGER = UUID.fromString(MANAGER_ID);
    private static final long MANAGED_PROJECT = 10L;
    private static final UUID DEPARTMENT = new UUID(0, 100);
    private static final UUID PROJECT = new UUID(0, 11);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope workspace;

    @TestConfiguration(proxyBeanMethods = false)
    static class WorkspaceAccessConfiguration {

        @Bean
        ReviewWorkspaceAccess reviewWorkspaceAccess(TwoTenantDatabases.RoutingTenantScope scope) {
            return new ReviewWorkspaceAccess() {
                @Override
                public Optional<Set<Long>> managedProjectIds(UUID accountId) {
                    try {
                        return scope.currentWorkspaceId().equals(TwoTenantDatabases.ACME) && accountId.equals(MANAGER)
                                ? Optional.of(Set.of(MANAGED_PROJECT, 11L))
                                : Optional.empty();
                    } catch (IllegalStateException unbound) {
                        return Optional.empty();
                    }
                }

                @Override
                public Optional<Map<Long, ProjectAccess>> readableProjects(UUID accountId) {
                    return managedProjectIds(accountId)
                            .map(ignored -> Map.of(
                                    MANAGED_PROJECT,
                                    new ProjectAccess(
                                            new Project(new UUID(0, 10), "Operations"), new UUID(0, 200), true),
                                    11L,
                                    new ProjectAccess(new Project(PROJECT, "Support"), DEPARTMENT, true),
                                    20L,
                                    new ProjectAccess(new Project(new UUID(0, 20), "Unmanaged"), DEPARTMENT, false)));
                }
            };
        }
    }

    @BeforeAll
    static void seedReviewer() {
        TwoTenantDatabases.tenantA()
                .update(
                        "insert into app_user (id, email, status, created_at) values (?, 'review-manager@example.test', 'ACTIVE', now())",
                        MANAGER);
    }

    @Test
    void listsScopedQueueAndAllEligibleIdsWithUnknownEntryTime_AC_009_1_AC_009_12() throws Exception {
        var own = TwoTenantDatabases.tenantA();
        UUID later = pending(own, 11L);
        UUID first = pending(own, 11L);
        pending(own, MANAGED_PROJECT);
        pending(own, 20L);
        pending(TwoTenantDatabases.tenantB(), 11L);
        own.update("update ticket set review_due_date = '2026-11-15' where ticket_id = ?", later);
        own.update(
                "update ticket set review_due_date = '2026-11-12', review_queue_entered_at = null where ticket_id = ?",
                first);

        inAcme(get("/api/v1/review-queue")
                        .param("departmentId", DEPARTMENT.toString())
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(first.toString()))
                .andExpect(jsonPath("$.content[0].project.projectId").value(PROJECT.toString()))
                .andExpect(jsonPath("$.content[0].enteredQueueAt").value(nullValue()))
                .andExpect(jsonPath("$.content[0].reviewDueDate").value("2026-11-12T00:00:00+07:00"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        inAcme(get("/api/v1/review-queue")
                        .param("departmentId", DEPARTMENT.toString())
                        .param("sort", "reviewDueDate,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(later.toString()));
        inAcme(get("/api/v1/review-queue/ticket-ids").param("departmentId", DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketIds", containsInAnyOrder(first.toString(), later.toString())));
    }

    @Test
    void readsPersistedSolutionAndEvidenceAndHidesUnmanagedPendingTickets_AC_009_2_AC_009_3() throws Exception {
        var own = TwoTenantDatabases.tenantA();
        UUID ticket = pending(own, MANAGED_PROJECT);
        UUID unmanaged = pending(own, 20L);
        UUID foreign = pending(TwoTenantDatabases.tenantB(), MANAGED_PROJECT);
        own.update(
                "insert into evidence (ticket_id, file_name, file_type, file_size) values (?, 'error.png', 'image/png', 32)",
                ticket);

        inAcme(get("/api/v1/tickets/{ticketId}", ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solution.resolutionSteps").value("Restart"))
                .andExpect(jsonPath("$.evidence[0].filename").value("error.png"))
                .andExpect(jsonPath("$.evidence[0].sizeBytes").value(32));
        inAcme(get("/api/v1/tickets/{ticketId}", unmanaged)).andExpect(status().isNotFound());
        inAcme(get("/api/v1/tickets/{ticketId}", foreign)).andExpect(status().isNotFound());

        own.update("update ticket set status = 'PUBLISHED' where ticket_id = ?", unmanaged);
        inAcme(get("/api/v1/tickets/{ticketId}", unmanaged)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({
        "approve,PUBLISHED,APPROVED",
        "reject,REJECTED,REJECTED",
        "request-changes,CHANGES_REQUESTED,CHANGES_REQUESTED"
    })
    void commitsEachHttpDecision_AC_009_4_AC_009_5_AC_009_6_AC_009_8(
            String route, String expectedStatus, String expectedDecision) throws Exception {
        var tenant = TwoTenantDatabases.tenantA();
        UUID id = pending(tenant, MANAGED_PROJECT);
        String feedback =
                switch (route) {
                    case "reject" -> ",\"reason\":\"Incorrect solution\"";
                    case "request-changes" -> ",\"comment\":\"Explain the workaround\"";
                    default -> "";
                };
        inAcme(route, body(id, feedback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        assertThat(ticketStatus(tenant, id)).isEqualTo(expectedStatus);
        assertThat(tenant.queryForMap(
                        "select decision, project_manager_id from review_decision where ticket_id = ?", id))
                .containsEntry("decision", expectedDecision)
                .containsEntry("project_manager_id", MANAGER);
    }

    @Test
    @WithAnonymousUser
    void deniesRequestsWithoutAuthentication_AC_009_3() throws Exception {
        inAcme("approve", body(UUID.randomUUID(), "")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithTSolveUser(id = MANAGER_ID, roles = Role.EMPLOYEE)
    void deniesAuthenticatedUsersWithoutProjectManagerRole_AC_009_3() throws Exception {
        inAcme("approve", body(UUID.randomUUID(), "")).andExpect(status().isForbidden());
    }

    @Test
    void deniesUnboundAndUnmanagedRequests_AC_009_3() throws Exception {
        var tenant = TwoTenantDatabases.tenantA();
        UUID id = pending(tenant, 20L);
        request("approve", body(id, "")).andExpect(status().isForbidden());
        inAcme("approve", body(id, ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        assertThat(ticketStatus(tenant, id)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void cannotDecideAnotherWorkspacesTicket_AC_009_3_AC_009_7() throws Exception {
        var tenant = TwoTenantDatabases.tenantB();
        UUID foreign = pending(tenant, MANAGED_PROJECT);
        var own = TwoTenantDatabases.tenantA();
        UUID local = pending(own, MANAGED_PROJECT);
        inAcme("approve", "{\"ticketIds\":[\"" + local + "\",\"" + foreign + "\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        assertThat(ticketStatus(tenant, foreign)).isEqualTo("PENDING_REVIEW");
        assertThat(ticketStatus(own, local)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void validatesFeedbackAndUniqueIds_AC_009_7() throws Exception {
        UUID id = pending(TwoTenantDatabases.tenantA(), MANAGED_PROJECT);
        inAcme("reject", body(id, ",\"reason\":\" \""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("MSG01"));
        inAcme("approve", "{\"ticketIds\":[\"" + id + "\",\"" + id + "\"]}").andExpect(status().isBadRequest());
        inAcme("request-changes", body(id, ",\"comment\":\"\\u2003\"")).andExpect(status().isBadRequest());
        assertThat(ticketStatus(TwoTenantDatabases.tenantA(), id)).isEqualTo("PENDING_REVIEW");
    }

    private ResultActions inAcme(String route, String json) {
        return inAcme(decisionRequest(route, json));
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

    private ResultActions request(String route, String json) throws Exception {
        return mvc.perform(decisionRequest(route, json));
    }

    private static RequestBuilder decisionRequest(String route, String json) {
        return post("/api/v1/reviews/" + route)
                .with(csrf())
                .contentType("application/json")
                .content(json);
    }

    private static String body(UUID id, String feedback) {
        return "{\"ticketIds\":[\"" + id + "\"]" + feedback + "}";
    }

    private static UUID pending(JdbcTemplate tenant, long projectId) {
        return tenant.queryForObject(
                """
                insert into ticket (source_ticket_id, project_id, source, status, title, symptoms,
                                    root_cause, resolution_steps, scope)
                values (?, ?, 'SOLUTION_FORM', 'PENDING_REVIEW', 'Login fails', 'Error', 'Token', 'Restart', 'Users')
                returning ticket_id
                """,
                UUID.class,
                UUID.randomUUID().toString(),
                projectId);
    }

    private static String ticketStatus(JdbcTemplate tenant, UUID id) {
        return tenant.queryForObject("select status from ticket where ticket_id = ?", String.class, id);
    }
}
