package vn.tsolve.ticket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.ProjectAccess;

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
@WithTSolveUser(id = TicketDetailIT.READER, roles = Role.TICKET_RESOLVER)
class TicketDetailIT {
    static final String READER = "00000000-0135-4000-8000-000000000001";
    static final String MANAGER = "00000000-0135-4000-8000-000000000002";
    static final String OUTSIDER = "00000000-0135-4000-8000-000000000003";

    @MockBean
    private AuditService audit;

    @MockBean
    private ProjectAccess importAccess;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ImportConfirmService confirms;

    @Autowired
    private ImportPreviewRepository previews;

    @Autowired
    private ImportPreviewRowRepository rows;

    private final JdbcTemplate db = TwoTenantDatabases.tenantA();
    private UUID department;
    private long departmentKey;
    private UUID project;
    private long projectKey;
    private UUID ticket;

    @BeforeAll
    static void accounts() {
        var db = TwoTenantDatabases.tenantA();
        for (String id : List.of(READER, MANAGER, OUTSIDER)) {
            db.update(
                    "insert into app_user(id, email, full_name, status, created_at) values (?, ?, 'Detail reader', 'ACTIVE', now())",
                    UUID.fromString(id),
                    id + "@example.test");
            db.update(
                    "insert into user_role(user_id, role, assigned_at) values (?, ?, now())",
                    UUID.fromString(id),
                    id.equals(MANAGER) ? "PROJECT_MANAGER" : "TICKET_RESOLVER");
        }
    }

    @BeforeEach
    void placement() {
        department = UUID.randomUUID();
        departmentKey = db.queryForObject(
                "insert into department(name, public_id) values (?, ?) returning department_id",
                Long.class,
                "Detail " + department,
                department);
        project = UUID.randomUUID();
        projectKey = project(project);
        UUID memberProject = UUID.randomUUID();
        project(memberProject);
        membership(READER, memberProject, "MEMBER");
        membership(MANAGER, project, "PROJECT_MANAGER");
        ticket = ticket(db, project, "PUBLISHED");
    }

    @Test
    @DisplayName("AC-008.1 reads Published tickets across Projects, including an inactive Project")
    void readsPublishedAcrossProjectsAndWithAnInactiveProject_AC_008_1() throws Exception {
        read(ticket).andExpect(status().isOk());
        db.update("update project set status = 'INACTIVE' where public_id = ?", project);
        read(ticket)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectId").value(project.toString()));
    }

    @Test
    @WithTSolveUser(id = OUTSIDER, roles = Role.TICKET_RESOLVER)
    @DisplayName("AC-008.1 AC-008.4 denies Published content outside the Department")
    void deniesAnotherDepartment_AC_008_1_AC_008_4() throws Exception {
        denied(ticket);
    }

    @Test
    @DisplayName("AC-008.2 reads a pending ticket only as its writer")
    void readsOnlyTheWriterOfANonPublishedTicket_AC_008_2() throws Exception {
        db.update(
                "update ticket set status = 'PENDING_REVIEW', writer_id = ? where ticket_id = ?",
                UUID.fromString(READER),
                ticket);
        read(ticket)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDecide").value(false));
        db.update("update ticket set writer_id = ? where ticket_id = ?", UUID.fromString(OUTSIDER), ticket);
        denied(ticket);
    }

    @Test
    @WithTSolveUser(id = MANAGER, roles = Role.PROJECT_MANAGER)
    @DisplayName("AC-008.2 AC-008.6 keeps Project Manager decisions and denies another Project's manager")
    void keepsManagerDecisionsAndDeniesAManagerOfAnotherProject_AC_008_2_AC_008_6() throws Exception {
        db.update("update ticket set status = 'PENDING_REVIEW' where ticket_id = ?", ticket);
        read(ticket)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDecide").value(true));
        db.update(
                "delete from project_membership where user_id = ? and project_id = ?",
                UUID.fromString(MANAGER),
                project);
        UUID other = UUID.randomUUID();
        project(other);
        membership(MANAGER, other, "PROJECT_MANAGER");
        denied(ticket);
    }

    @Test
    @DisplayName("AC-008.3 denies inactive, deleted and missing placement, even to the writer")
    void deniesInactiveDeletedAndMissingPlacementEvenToTheWriter_AC_008_3() throws Exception {
        db.update(
                "update ticket set writer_id = ?, status = 'PENDING_REVIEW' where ticket_id = ?",
                UUID.fromString(READER),
                ticket);
        for (String status : List.of("INACTIVE", "DELETED")) {
            db.update("update department set status = ? where department_id = ?", status, departmentKey);
            denied(ticket);
        }
        db.update("update department set status = 'ACTIVE' where department_id = ?", departmentKey);
        db.update("update ticket set project_id = ? where ticket_id = ?", UUID.randomUUID(), ticket);
        denied(ticket);
    }

    @Test
    @WithTSolveUser(id = READER, roles = Role.EMPLOYEE)
    @DisplayName("AC-008.4 denies a caller with only Employee")
    void deniesAnEmployeeOnlyCaller_AC_008_4() throws Exception {
        denied(ticket);
    }

    @Test
    @DisplayName("AC-008.5 returns MSG04 for missing and foreign Workspace ticket ids")
    void hidesMissingAndForeignWorkspaceTickets_AC_008_5() throws Exception {
        read(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
        UUID foreign = ticket(TwoTenantDatabases.tenantB(), project, "PUBLISHED");
        read(foreign)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG04"));
    }

    @Test
    @DisplayName("AC-008.6 returns Department, resolved date, source and existing fields with UUID compatibility")
    void returnsPlacementDateSourceAndExistingFieldsForPublicAndLegacyIds_AC_008_6() throws Exception {
        String link = "https://example.test/browse/DETAIL-135";
        db.update("update ticket set resolved_date = '2026-11-14', source_link = ? where ticket_id = ?", link, ticket);
        UUID publicId = db.queryForObject("select public_id from ticket where ticket_id = ?", UUID.class, ticket);
        for (UUID id : List.of(ticket, publicId)) {
            read(id).andExpect(status().isOk())
                    .andExpect(jsonPath("$.department.id").value(department.toString()))
                    .andExpect(jsonPath("$.department.name").value("Detail " + department))
                    .andExpect(jsonPath("$.resolvedDate").value("2026-11-14"))
                    .andExpect(jsonPath("$.sourceLink").value(link))
                    .andExpect(jsonPath("$.solution.resolutionSteps").value("Refresh token"))
                    .andExpect(jsonPath("$.canDecide").value(false));
        }
        db.update(
                "update ticket set project_id = ?, resolved_date = null, source_link = null where ticket_id = ?",
                new UUID(0, projectKey),
                ticket);
        read(ticket)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectId").value(project.toString()))
                .andExpect(jsonPath("$.sourceLink").value(nullValue()))
                .andExpect(jsonPath("$.resolvedDate").value(nullValue()));
    }

    @Test
    @WithTSolveUser(id = MANAGER, roles = Role.PROJECT_MANAGER)
    @DisplayName("AC-008.7 Jira Finish stores the preview issue's source link")
    void jiraFinishPersistsThePreviewIssueLink_AC_008_7() {
        String link = "https://example.test/browse/DETAIL-" + ticket;
        when(importAccess.isActive(project)).thenReturn(true);
        when(importAccess.managesActive(MANAGER, project)).thenReturn(true);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            var preview = previews.save(ImportPreview.jira(
                    MANAGER, project, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14), false, Instant.now()));
            var row = ImportPreviewRow.jiraIssue(
                    preview.getId(),
                    1,
                    new ImportPreviewRow.Values(
                            "D-" + ticket,
                            "Login restored",
                            "Description",
                            "Writer",
                            "2026-11-14",
                            null,
                            null,
                            null,
                            null,
                            null),
                    link,
                    List.of());
            row.fillSolution("Login failed", "Token", "Refresh token", null, "Users");
            rows.save(row);
            confirms.confirm(MANAGER, preview.getId());
            return null;
        });
        assertThat(db.queryForObject(
                        "select source_link from ticket where source_ticket_id = ?", String.class, "D-" + ticket))
                .isEqualTo(link);
    }

    private long project(UUID id) {
        return db.queryForObject(
                "insert into project(department_id, jira_project_key, public_id) values (?, ?, ?) returning project_id",
                Long.class,
                departmentKey,
                "D" + id.toString().substring(0, 12),
                id);
    }

    private void membership(String account, UUID projectId, String role) {
        db.update(
                "insert into project_membership(user_id, project_id, role_in_project) values (?, ?, ?)",
                UUID.fromString(account),
                projectId,
                role);
    }

    private static UUID ticket(JdbcTemplate db, UUID projectId, String status) {
        return db.queryForObject("""
                insert into ticket(source_ticket_id, project_id, source, status, title, symptoms, root_cause,
                                   resolution_steps, scope)
                values (?, ?, 'SOLUTION_FORM', ?, 'Login restored', 'Login failed', 'Token', 'Refresh token', 'Users')
                returning ticket_id
                """, UUID.class, UUID.randomUUID().toString(), projectId, status);
    }

    private ResultActions read(UUID id) throws Exception {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(get("/api/v1/tickets/{ticketId}", id));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private void denied(UUID id) throws Exception {
        read(id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"))
                .andExpect(jsonPath("$.solution").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist());
    }
}
