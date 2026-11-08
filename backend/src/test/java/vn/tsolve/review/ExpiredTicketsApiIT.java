package vn.tsolve.review;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewWorkspaceAccess;
import vn.tsolve.ticket.dto.TicketReviewView.Project;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;

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
@Import({TwoTenantDatabases.class, ExpiredTicketsApiIT.WorkspaceAccessConfiguration.class})
@WithTSolveUser(id = ExpiredTicketsApiIT.MANAGER_ID, roles = Role.PROJECT_MANAGER)
class ExpiredTicketsApiIT {

    static final String MANAGER_ID = "b1bdcf21-77f1-4ab2-8d56-2170d3f0be33";
    private static final UUID MANAGER = UUID.fromString(MANAGER_ID);
    private static final UUID OPS = new UUID(0, 710);
    private static final UUID SUPPORT = new UUID(0, 711);
    private static final UUID UNMANAGED = new UUID(0, 712);
    private static final UUID OPS_DEPARTMENT = new UUID(0, 810);
    private static final UUID SUPPORT_DEPARTMENT = new UUID(0, 811);
    private static final UUID OTHER_DEPARTMENT = new UUID(0, 812);

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

    private static UUID first;
    private static UUID second;
    private static UUID legacy;

    @BeforeAll
    static void seedExpiredTickets() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        first = ticket(db, OPS, "EXP-1", "Alpha", "EXPIRED", "SOLUTION_FORM", "2026-11-03");
        second = ticket(db, SUPPORT, "EXP-2", "Beta", "EXPIRED", "SOLUTION_FORM", "2026-11-04");
        legacy = ticket(db, OPS, "EXP-3", "Gamma", "EXPIRED", "SOLUTION_FORM", null);
        ticket(db, UNMANAGED, "EXP-4", "Hidden", "EXPIRED", "SOLUTION_FORM", "2026-11-05");
        ticket(db, OPS, "EXP-5", "Pending", "PENDING_REVIEW", "SOLUTION_FORM", "2026-11-05");
        ticket(db, OPS, "EXP-6", "Imported", "EXPIRED", "CSV", "2026-11-05");
        ticket(TwoTenantDatabases.tenantB(), OPS, "EXP-7", "Foreign", "EXPIRED", "SOLUTION_FORM", "2026-11-05");
    }

    @Test
    void listsOnlyManagedExpiredTickets_AC_013_1() throws Exception {
        inAcme(get("/api/v1/expired-tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].ticketId")
                        .value(contains(legacy.toString(), second.toString(), first.toString())))
                .andExpect(jsonPath("$.content[2].sourceTicketId").value("EXP-1"))
                .andExpect(jsonPath("$.content[2].title").value("Alpha"))
                .andExpect(jsonPath("$.content[2].project.projectId").value(OPS.toString()))
                .andExpect(jsonPath("$.content[2].project.name").value("Operations"))
                .andExpect(jsonPath("$.content[2].source").value("SOLUTION_FORM"))
                .andExpect(jsonPath("$.content[2].authorName").value("Minh Tran"))
                .andExpect(jsonPath("$.content[2].enteredQueueAt").value("2026-11-01T01:00:00Z"))
                .andExpect(jsonPath("$.content[2].reviewDueDate").value("2026-11-03T00:00:00+07:00"));
    }

    @Test
    void returnsEveryIdAcrossPagesAndNarrowsByDepartment_AC_013_2() throws Exception {
        inAcme(get("/api/v1/expired-tickets").param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].ticketId").value(contains(second.toString())))
                .andExpect(jsonPath("$.page.totalPages").value(3));
        inAcme(get("/api/v1/expired-tickets/ticket-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketIds")
                        .value(containsInAnyOrder(first.toString(), second.toString(), legacy.toString())));
        inAcme(get("/api/v1/expired-tickets").param("departmentId", SUPPORT_DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].ticketId").value(contains(second.toString())));
        inAcme(get("/api/v1/expired-tickets/ticket-ids").param("departmentId", SUPPORT_DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketIds").value(contains(second.toString())));
        inAcme(get("/api/v1/expired-tickets").param("departmentId", OTHER_DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(empty()))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        inAcme(get("/api/v1/expired-tickets/ticket-ids").param("departmentId", OTHER_DEPARTMENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketIds").value(empty()));
    }

    @Test
    void derivesVietnamExpiryAndPreviousStatusWithLegacyFallback_AC_013_3() throws Exception {
        inAcme(get("/api/v1/expired-tickets").param("sort", "expiredAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].expiredAt").value("2026-11-04T00:00:00+07:00"))
                .andExpect(jsonPath("$.content[1].expiredAt").value("2026-11-05T00:00:00+07:00"))
                .andExpect(jsonPath("$.content[2].expiredAt").value("2026-11-06T07:00:00+07:00"))
                .andExpect(jsonPath("$.content[*].previousStatus")
                        .value(contains("PENDING_REVIEW", "PENDING_REVIEW", "PENDING_REVIEW")));
    }

    @ParameterizedTest
    @CsvSource({
        "expiredAt,asc,EXP-1",
        "expiredAt,desc,EXP-3",
        "sourceTicketId,asc,EXP-1",
        "sourceTicketId,desc,EXP-3",
        "title,asc,EXP-1",
        "title,desc,EXP-3",
        "project,asc,Operations",
        "project,desc,Support"
    })
    void appliesEverySupportedSort_AC_013_4(String field, String direction, String expected) throws Exception {
        inAcme(get("/api/v1/expired-tickets").param("sort", field + "," + direction))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(field.equals("project") ? "$.content[0].project.name" : "$.content[0].sourceTicketId")
                                .value(expected));
    }

    @ParameterizedTest
    @CsvSource({"sort,title,sideways", "sort,unknown,asc", "sort,expiredAt,", "page,-1,", "size,0,", "size,101,"})
    void refusesInvalidSortAndPagingWithMsg06_AC_013_4(String param, String value, String direction) throws Exception {
        inAcme(get("/api/v1/expired-tickets")
                        .param(
                                param,
                                param.equals("sort") ? value + "," + (direction == null ? "" : direction) : value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG06"));
    }

    @Test
    @WithAnonymousUser
    void deniesAnonymousListAndIds_AC_013_7() throws Exception {
        for (String path : java.util.List.of("/api/v1/expired-tickets", "/api/v1/expired-tickets/ticket-ids")) {
            inAcme(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @WithTSolveUser(id = MANAGER_ID, roles = Role.EMPLOYEE)
    void deniesNonManagerListAndIds_AC_013_7() throws Exception {
        for (String path : java.util.List.of("/api/v1/expired-tickets", "/api/v1/expired-tickets/ticket-ids")) {
            inAcme(get(path)).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithTSolveUser(id = "33333333-3333-4333-8333-333333333333", roles = Role.PROJECT_MANAGER)
    void deniesUnverifiedAccountListAndIds_AC_013_7() throws Exception {
        for (String path : java.util.List.of("/api/v1/expired-tickets", "/api/v1/expired-tickets/ticket-ids")) {
            inAcme(get(path)).andExpect(status().isForbidden());
        }
    }

    @Test
    void breaksSortTiesByTicketUuidAcrossPages_AC_013_4() throws Exception {
        var db = TwoTenantDatabases.tenantA();
        UUID a = ticket(db, SUPPORT, "EXP-TIE-A", "Same", "EXPIRED", "SOLUTION_FORM", "2026-12-01");
        UUID b = ticket(db, SUPPORT, "EXP-TIE-B", "Same", "EXPIRED", "SOLUTION_FORM", "2026-12-01");
        var ids = java.util.List.of(a, b).stream().sorted().toList();
        try {
            for (int page = 0; page < 2; page++) {
                inAcme(get("/api/v1/expired-tickets")
                                .param("sort", "expiredAt,desc")
                                .param("size", "1")
                                .param("page", Integer.toString(page)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].ticketId")
                                .value(ids.get(page).toString()));
            }
        } finally {
            db.update("delete from ticket where ticket_id in (?, ?)", a, b);
        }
    }

    @Test
    void deniesUnboundAndOtherWorkspaceReads_AC_013_7() throws Exception {
        for (String path : java.util.List.of("/api/v1/expired-tickets", "/api/v1/expired-tickets/ticket-ids")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
            workspace.runIn(TwoTenantDatabases.BETA, () -> {
                try {
                    return mvc.perform(get(path)).andExpect(status().isForbidden());
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
        }
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

    private static UUID ticket(
            JdbcTemplate db, UUID project, String key, String title, String status, String source, String due) {
        return db.queryForObject(
                """
                insert into ticket (source_ticket_id, project_id, source, status, writer, title, symptoms, root_cause, resolution_steps, scope, review_due_date, updated_at, review_queue_entered_at)
                values (?, ?, ?, ?, 'Minh Tran', ?, 'Error', 'Token', 'Restart', 'Users', cast(? as date), '2026-11-06T00:00:00Z', '2026-11-01T01:00:00Z') returning ticket_id
                """,
                UUID.class,
                key,
                project,
                source,
                status,
                title,
                due);
    }
}
