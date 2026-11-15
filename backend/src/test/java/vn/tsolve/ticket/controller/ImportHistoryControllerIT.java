package vn.tsolve.ticket.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.TicketTestDatabase;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(TicketTestDatabase.class)
@TestPropertySource(
        properties = {
            "tsolve.stub.projects=11111111-1111-1111-1111-111111111111:00000000-0000-0000-0000-000000000001",
            "tsolve.cors.allowed-origins=http://localhost:5173",
            "server.port=0",
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
class ImportHistoryControllerIT {
    private static final String URL = "/api/v1/imports/history";
    private static final UUID PROJECT = UUID.fromString("a1000000-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("a1000000-0000-4000-8000-000000000002");
    private static final UUID UNMANAGED = UUID.fromString("a1000000-0000-4000-8000-000000000003");
    private static final TSolveUser PM = new TSolveUser(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "history-pm@example.test",
            "Nguyễn Văn An",
            Set.of(Role.PROJECT_MANAGER));

    @MockBean
    private AuditService audit;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID department;
    private UUID legacyProject;

    @BeforeEach
    void seedMemberships() {
        jdbc.execute("delete from ticket_decision");
        jdbc.execute("delete from ticket");
        jdbc.execute("delete from import_batch");
        jdbc.execute("delete from import_preview");
        jdbc.update("delete from project_membership where project_id in (?, ?, ?)", PROJECT, SECOND, UNMANAGED);
        jdbc.update("delete from project where public_id in (?, ?, ?)", PROJECT, SECOND, UNMANAGED);
        jdbc.update("delete from department where name = 'Import History Department'");
        jdbc.update(
                "insert into app_user (id, email, full_name, status, created_at) values (?, ?, ?, 'ACTIVE', now())"
                        + " on conflict do nothing",
                PM.id(),
                PM.email(),
                PM.fullName());
        long departmentId = jdbc.queryForObject(
                "insert into department (name) values ('Import History Department') returning department_id",
                Long.class);
        department = jdbc.queryForObject(
                "select public_id from department where department_id = ?", UUID.class, departmentId);
        long projectId = jdbc.queryForObject(
                "insert into project (public_id, department_id, jira_project_key)"
                        + " values (?, ?, 'HISTORYA') returning project_id",
                Long.class,
                PROJECT,
                departmentId);
        legacyProject = new UUID(0, projectId);
        jdbc.update(
                "insert into project (public_id, department_id, jira_project_key, status) values (?, ?, 'HISTORYB',"
                        + " 'REMOVED')",
                SECOND,
                departmentId);
        jdbc.update(
                "insert into project (public_id, department_id, jira_project_key) values (?, ?, 'HISTORYC')",
                UNMANAGED,
                departmentId);
        for (UUID project : new UUID[] {PROJECT, SECOND}) {
            jdbc.update(
                    "insert into project_membership (user_id, project_id, role_in_project) values (?, ?,"
                            + " 'PROJECT_MANAGER')",
                    PM.id(),
                    project);
        }
        jdbc.update(
                "insert into project_membership (user_id, project_id, role_in_project) values (?, ?, 'MEMBER')",
                PM.id(),
                UNMANAGED);
    }

    private long batch(UUID project, String performer, String source, String at, String state, String error) {
        return jdbc.queryForObject(
                """
                insert into import_batch (project_id, performed_by, source, file_name, date_from, date_to,
                    published_count, removed_count, error_count, status, error, created_at)
                values (?, ?, ?, ?, ?, ?, ?, 2, 3, ?, ?, ?) returning id
                """,
                Long.class,
                project,
                performer,
                source,
                "CSV".equals(source) ? "tickets.csv" : null,
                "JIRA_IMPORT".equals(source) ? java.sql.Date.valueOf("2026-10-01") : null,
                "JIRA_IMPORT".equals(source) ? java.sql.Date.valueOf("2026-10-31") : null,
                "COMPLETED".equals(state) ? 7 : 0,
                state,
                error,
                Timestamp.from(Instant.parse(at)));
    }

    private long batch(UUID project, String source, String at) {
        return batch(project, PM.getName(), source, at, "COMPLETED", null);
    }

    private ResultActions as(TSolveUser user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(
                request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()))));
    }

    @Test
    @DisplayName("AC-007.1 lists only currently managed Projects, newest first with stable paging")
    void managedScopeAndPaging_AC_007_1() throws Exception {
        long oldest = batch(PROJECT, "CSV", "2026-11-12T00:00:00Z");
        long tied = batch(SECOND, "MANUAL", "2026-11-14T00:00:00Z");
        long newest = batch(PROJECT, "JIRA_IMPORT", "2026-11-14T00:00:00Z");
        batch(UNMANAGED, "CSV", "2026-11-15T00:00:00Z");
        as(PM, get(URL).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].batchId").value(newest))
                .andExpect(jsonPath("$.content[1].batchId").value(tied))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        as(PM, get(URL).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].batchId").value(oldest));
        as(PM, get(URL)).andExpect(jsonPath("$.page.size").value(20));
        jdbc.update("delete from project_membership where user_id = ? and project_id = ?", PM.id(), PROJECT);
        as(PM, get(URL))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].batchId").value(tied));
    }

    @Test
    @DisplayName("AC-007.2 AC-007.3 AC-007.4 returns every field, legacy Project placement and only the MSG error")
    void fullItemAndLegacyProject_AC_007_2_AC_007_3_AC_007_4() throws Exception {
        long id = batch(
                legacyProject,
                PM.getName(),
                "JIRA_IMPORT",
                "2026-11-14T16:30:00Z",
                "FAILED",
                "JiraUnavailableException MSG50 private error details MSG33");
        as(PM, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].batchId").value(id))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-14T16:30:00Z"))
                .andExpect(jsonPath("$.content[0].performedBy").value(PM.fullName()))
                .andExpect(jsonPath("$.content[0].project.projectId").value(PROJECT.toString()))
                .andExpect(jsonPath("$.content[0].project.projectKey").value("HISTORYA"))
                .andExpect(jsonPath("$.content[0].department.id").value(department.toString()))
                .andExpect(jsonPath("$.content[0].department.name").value("Import History Department"))
                .andExpect(jsonPath("$.content[0].source").value("JIRA_IMPORT"))
                .andExpect(jsonPath("$.content[0].dateFrom").value("2026-10-01"))
                .andExpect(jsonPath("$.content[0].dateTo").value("2026-10-31"))
                .andExpect(jsonPath("$.content[0].fileName").isEmpty())
                .andExpect(jsonPath("$.content[0].publishedCount").value(0))
                .andExpect(jsonPath("$.content[0].removedCount").value(2))
                .andExpect(jsonPath("$.content[0].errorCount").value(3))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].errorCode").value("MSG50"))
                .andExpect(jsonPath("$.content[0].error").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"stub-user", "22222222-2222-2222-2222-222222222222"})
    @DisplayName("AC-007.3 preserves a non-UUID performer or an unknown account id")
    void rawPerformer_AC_007_3(String performer) throws Exception {
        batch(PROJECT, performer, "CSV", "2026-11-14T00:00:00Z", "COMPLETED", null);
        as(PM, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].performedBy").value(performer))
                .andExpect(jsonPath("$.content[0].fileName").value("tickets.csv"))
                .andExpect(jsonPath("$.content[0].dateFrom").isEmpty())
                .andExpect(jsonPath("$.content[0].errorCode").isEmpty());
    }

    @Test
    @DisplayName("AC-007.5 combines Project, source and inclusive Vietnam calendar-day filters")
    void filters_AC_007_5() throws Exception {
        batch(PROJECT, "CSV", "2026-11-13T16:59:59Z");
        long start = batch(PROJECT, "CSV", "2026-11-13T17:00:00Z");
        long late = batch(PROJECT, "CSV", "2026-11-14T16:30:00Z");
        batch(PROJECT, "CSV", "2026-11-14T17:00:00Z");
        batch(PROJECT, "MANUAL", "2026-11-14T12:00:00Z");
        batch(SECOND, "CSV", "2026-11-14T12:00:00Z");
        as(
                        PM,
                        get(URL).param("projectId", PROJECT.toString())
                                .param("source", "CSV")
                                .param("dateFrom", "2026-11-14")
                                .param("dateTo", "2026-11-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].batchId").value(late))
                .andExpect(jsonPath("$.content[1].batchId").value(start));
        as(PM, get(URL).param("projectId", UNMANAGED.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        as(PM, get(URL).param("projectId", PROJECT.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(5));
        as(PM, get(URL).param("source", "CSV"))
                .andExpect(jsonPath("$.page.totalElements").value(5));
        as(PM, get(URL).param("source", "MANUAL"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        as(PM, get(URL).param("source", "SOLUTION_FORM"))
                .andExpect(jsonPath("$.content.length()").value(0));
        as(PM, get(URL).param("dateFrom", "2026-11-14"))
                .andExpect(jsonPath("$.page.totalElements").value(5));
        as(PM, get(URL).param("dateTo", "2026-11-14"))
                .andExpect(jsonPath("$.page.totalElements").value(5));
    }

    @Test
    @DisplayName("AC-007.6 reversed date ranges answer 400 MSG39")
    void invalidRange_AC_007_6() throws Exception {
        as(PM, get(URL).param("dateFrom", "2026-11-15").param("dateTo", "2026-11-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG39"));
    }

    @Test
    @DisplayName("AC-007.7 a Ticket Resolver cannot read Import History")
    void noManagerRole_AC_007_7() throws Exception {
        TSolveUser resolver = new TSolveUser(PM.id(), PM.email(), PM.fullName(), Set.of(Role.TICKET_RESOLVER));
        as(resolver, get(URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "source=UNKNOWN", "dateFrom=bad", "projectId=bad"})
    @DisplayName("AC-007.1 AC-007.5 rejects invalid paging and filter values")
    void invalidParameters_AC_007_1_AC_007_5(String parameter) throws Exception {
        String[] pair = parameter.split("=");
        as(PM, get(URL).param(pair[0], pair[1])).andExpect(status().isBadRequest());
    }
}
