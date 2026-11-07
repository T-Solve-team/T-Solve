package vn.tsolve.review;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewWorkspaceAccess;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;

/** Review History read API over stored decisions (STORY-011, KAN-159). */
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
@Import({TwoTenantDatabases.class, ReviewHistoryApiIT.WorkspaceAccessConfiguration.class})
@WithTSolveUser(id = ReviewHistoryApiIT.MANAGER_ID, roles = Role.PROJECT_MANAGER)
class ReviewHistoryApiIT {

    static final String MANAGER_ID = "6f0e3bbf-4e0b-4b58-9a43-6a5f1b2c7d59";
    private static final UUID MANAGER = UUID.fromString(MANAGER_ID);
    private static final UUID NAMELESS_MANAGER = UUID.fromString("0b3f5b7e-2a54-4b0e-8f0f-77c1d6a4e211");
    private static final UUID OPS = new UUID(0, 510);
    private static final UUID SUPPORT = new UUID(0, 511);
    private static final UUID UNMANAGED = new UUID(0, 512);
    private static final UUID OPS_DEPARTMENT = new UUID(0, 610);
    private static final UUID SUPPORT_DEPARTMENT = new UUID(0, 611);
    private static final UUID OTHER_DEPARTMENT = new UUID(0, 612);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope workspace;

    @TestConfiguration(proxyBeanMethods = false)
    static class WorkspaceAccessConfiguration {

        @Bean
        @Primary
        ReviewWorkspaceAccess reviewWorkspaceAccess(TwoTenantDatabases.RoutingTenantScope scope) {
            return new ReviewWorkspaceAccess() {
                @Override
                public Optional<Set<UUID>> managedProjectIds(UUID accountId) {
                    return readableProjects(accountId).map(projects -> Set.of(OPS, SUPPORT));
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
                            OPS, new ProjectAccess(new Project(OPS, "Operations"), OPS_DEPARTMENT, true),
                            SUPPORT, new ProjectAccess(new Project(SUPPORT, "Support"), SUPPORT_DEPARTMENT, true),
                            UNMANAGED, new ProjectAccess(new Project(UNMANAGED, "Finance"), OTHER_DEPARTMENT, false)));
                }
            };
        }
    }

    private static UUID approved;
    private static UUID rejected;
    private static UUID changesRequested;

    @BeforeAll
    static void seedDecisions() {
        JdbcTemplate acme = TwoTenantDatabases.tenantA();
        acme.update(
                "insert into app_user (id, email, full_name, status, created_at) values (?, 'history-pm@example.test', 'Lan Pham', 'ACTIVE', now())",
                MANAGER);
        acme.update(
                "insert into app_user (id, email, status, created_at) values (?, 'nameless-pm@example.test', 'ACTIVE', now())",
                NAMELESS_MANAGER);
        approved = decision(acme, OPS, "HIS-1", MANAGER, "APPROVED", null, "2026-11-03T09:00:00+07:00");
        rejected =
                decision(acme, SUPPORT, "HIS-2", MANAGER, "REJECTED", "Wrong root cause", "2026-11-04T23:30:00+07:00");
        changesRequested = decision(
                acme,
                OPS,
                "HIS-3",
                NAMELESS_MANAGER,
                "CHANGES_REQUESTED",
                "Add the VPN step",
                "2026-11-05T00:10:00+07:00");
        decision(acme, UNMANAGED, "HIS-4", MANAGER, "REJECTED", "Hidden", "2026-11-05T10:00:00+07:00");
        TwoTenantDatabases.tenantB()
                .update(
                        "insert into app_user (id, email, full_name, status, created_at) values (?, 'history-pm@example.test', 'Lan Pham', 'ACTIVE', now())",
                        MANAGER);
        decision(
                TwoTenantDatabases.tenantB(),
                OPS,
                "HIS-5",
                MANAGER,
                "REJECTED",
                "Other company",
                "2026-11-05T11:00:00+07:00");
    }

    @Test
    void listsOnlyManagedDecisionsNewestFirstWithStoredContent_AC_011_1_AC_011_2() throws Exception {
        inAcme(get("/api/v1/review-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].decisionId")
                        .value(contains(changesRequested.toString(), rejected.toString(), approved.toString())))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].decision").value("REQUEST_CHANGES"))
                .andExpect(jsonPath("$.content[0].comment").value("Add the VPN step"))
                .andExpect(jsonPath("$.content[0].reason").value(nullValue()))
                .andExpect(jsonPath("$.content[0].actorId").value(NAMELESS_MANAGER.toString()))
                .andExpect(jsonPath("$.content[0].actorName").value("nameless-pm@example.test"))
                .andExpect(jsonPath("$.content[1].decision").value("REJECT"))
                .andExpect(jsonPath("$.content[1].reason").value("Wrong root cause"))
                .andExpect(jsonPath("$.content[1].comment").value(nullValue()))
                .andExpect(jsonPath("$.content[1].actorName").value("Lan Pham"))
                .andExpect(jsonPath("$.content[1].sourceTicketId").value("HIS-2"))
                .andExpect(jsonPath("$.content[1].title").value("Login fails"))
                .andExpect(jsonPath("$.content[1].authorName").value("Minh Tran"))
                .andExpect(jsonPath("$.content[1].project.projectId").value(SUPPORT.toString()))
                .andExpect(jsonPath("$.content[1].project.name").value("Support"))
                .andExpect(jsonPath("$.content[2].decision").value("APPROVE"))
                .andExpect(jsonPath("$.content[2].reason").value(nullValue()))
                .andExpect(jsonPath("$.content[2].comment").value(nullValue()));
    }

    @Test
    void sortsOldestFirstAndPages_AC_011_1() throws Exception {
        inAcme(get("/api/v1/review-history")
                        .param("sort", "timestamp,asc")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].decisionId").value(contains(changesRequested.toString())))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void filtersByDepartmentDecisionAndVietnamCalendarDates_AC_011_3() throws Exception {
        inAcme(get("/api/v1/review-history").param("departmentId", SUPPORT_DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].decisionId").value(contains(rejected.toString())));
        inAcme(get("/api/v1/review-history").param("decision", "APPROVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].decisionId").value(contains(approved.toString())));
        inAcme(get("/api/v1/review-history").param("dateFrom", "2026-11-04").param("dateTo", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].decisionId").value(contains(rejected.toString())));
        inAcme(get("/api/v1/review-history").param("decision", "UNPUBLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(empty()))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        inAcme(get("/api/v1/review-history").param("departmentId", OTHER_DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(empty()));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "2026-11-04,",
                ",2026-11-04",
                "2026-11-05,2026-11-04",
                "2026-02-30,2026-03-01",
                "+2026-11-04,2026-11-05"
            },
            ignoreLeadingAndTrailingWhitespace = false)
    void refusesInvalidDateRangesWithMsg39_AC_011_3(String from, String to) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/review-history");
        if (from != null) {
            request.param("dateFrom", from);
        }
        if (to != null) {
            request.param("dateTo", to);
        }
        inAcme(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG39"));
    }

    @ParameterizedTest
    @CsvSource({"sort,title", "sort,'timestamp,sideways'", "decision,DELETE", "page,-1", "size,0", "size,101"})
    void refusesOtherInvalidParametersWithMsg06_AC_011_4(String name, String value) throws Exception {
        inAcme(get("/api/v1/review-history").param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithAnonymousUser
    void deniesAnonymousCallers_AC_011_5() throws Exception {
        inAcme(get("/api/v1/review-history")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithTSolveUser(id = MANAGER_ID, roles = Role.EMPLOYEE)
    void deniesCallersWithoutProjectManager_AC_011_5() throws Exception {
        inAcme(get("/api/v1/review-history")).andExpect(status().isForbidden());
    }

    @Test
    void deniesUnverifiedWorkspaceAccess_AC_011_5() throws Exception {
        mvc.perform(get("/api/v1/review-history")).andExpect(status().isForbidden());
        workspace.runIn(TwoTenantDatabases.BETA, () -> {
            try {
                return mvc.perform(get("/api/v1/review-history")).andExpect(status().isForbidden());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void showsANewHttpDecisionInHistory_AC_011_6() throws Exception {
        UUID ticket = ticket(TwoTenantDatabases.tenantA(), SUPPORT, "HIS-6", "PENDING_REVIEW");
        inAcme(post("/api/v1/reviews/request-changes")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"ticketIds\":[\"" + ticket + "\"],\"comment\":\"Attach the log\"}"))
                .andExpect(status().isOk());
        inAcme(get("/api/v1/review-history").param("departmentId", SUPPORT_DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(ticket.toString()))
                .andExpect(jsonPath("$.content[0].comment").value("Attach the log"));
        TwoTenantDatabases.tenantA().update("delete from review_decision where ticket_id = ?", ticket);
        TwoTenantDatabases.tenantA().update("delete from ticket where ticket_id = ?", ticket);
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

    private static UUID decision(
            JdbcTemplate db, UUID project, String key, UUID reviewer, String decision, String text, String at) {
        String status =
                switch (decision) {
                    case "APPROVED" -> "PUBLISHED";
                    default -> decision;
                };
        UUID ticket = ticket(db, project, key, status);
        return db.queryForObject(
                """
                insert into review_decision (ticket_id, project_manager_id, decision, reason_or_comment, decided_at)
                values (?, ?, ?, ?, cast(? as timestamptz))
                returning review_decision_id
                """,
                UUID.class,
                ticket,
                reviewer,
                decision,
                text,
                at);
    }

    private static UUID ticket(JdbcTemplate db, UUID project, String key, String status) {
        return db.queryForObject(
                """
                insert into ticket (source_ticket_id, project_id, source, status, writer, title, symptoms,
                                    root_cause, resolution_steps, scope)
                values (?, ?, 'SOLUTION_FORM', ?, 'Minh Tran', 'Login fails', 'Error', 'Token', 'Restart', 'Users')
                returning ticket_id
                """,
                UUID.class,
                key,
                project,
                status);
    }
}
