package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewWorkspaceAccess;

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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope workspace;

    @TestConfiguration(proxyBeanMethods = false)
    static class WorkspaceAccessConfiguration {

        @Bean
        ReviewWorkspaceAccess reviewWorkspaceAccess(TwoTenantDatabases.RoutingTenantScope scope) {
            return accountId -> {
                try {
                    return scope.currentWorkspaceId().equals(TwoTenantDatabases.ACME) && accountId.equals(MANAGER)
                            ? Optional.of(Set.of(MANAGED_PROJECT))
                            : Optional.empty();
                } catch (IllegalStateException unbound) {
                    return Optional.empty();
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
        return workspace.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return request(route, json);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private ResultActions request(String route, String json) throws Exception {
        return mvc.perform(post("/api/v1/reviews/" + route)
                .with(csrf())
                .contentType("application/json")
                .content(json));
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
