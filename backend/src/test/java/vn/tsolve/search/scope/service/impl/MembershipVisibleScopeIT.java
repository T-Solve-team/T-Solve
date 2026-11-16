package vn.tsolve.search.scope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.search.list.dto.TicketListQuery;
import vn.tsolve.search.list.dto.TicketSummaryResponse;
import vn.tsolve.search.list.service.TicketListService;
import vn.tsolve.search.scope.service.VisibleScope;

/** Shares the existing tenant server; starts no new container. */
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
            "DB_PASSWORD=unused",
            "tsolve.worker.enabled=false",
            "SEARCH_DEV_SCOPE="
        })
@AutoConfigureMockMvc
@Import(TwoTenantDatabases.class)
class MembershipVisibleScopeIT {
    @Autowired
    private TenantScope tenants;

    @Autowired
    private VisibleScope scope;

    @Autowired
    private TicketListService tickets;

    private JdbcTemplate jdbc;
    private long itDepartment;
    private long hrDepartment;
    private UUID itProject;
    private UUID hrProject;

    @BeforeEach
    void seed() {
        jdbc = TwoTenantDatabases.tenantA();
        jdbc.execute("TRUNCATE ticket, project, department, app_user CASCADE");
        itDepartment = department("IT Support", "ACTIVE");
        hrDepartment = department("HR", "ACTIVE");
        itProject = project(itDepartment, "ITS");
        hrProject = project(hrDepartment, "HR");
        ticket(itProject, "ITS-1", "PUBLISHED", "Other", null);
        ticket(hrProject, "HR-1", "PUBLISHED", "Other", null);
        ticket(itProject, "ITS-2", "PENDING_REVIEW", "Other", null);
        ticket(hrProject, "HR-2", "PENDING_REVIEW", "Other", null);
    }
    /** Shared tenant database: leave no Department behind for other ITs (unique Department names). */
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE ticket, project, department, app_user CASCADE");
    }

    @Test
    void itSupportUserSeesOnlyItSupport_AC_006_1_AC_006_2() {
        var user = user("IT Resolver", itDepartment);
        assertThat(tenants.runIn(TwoTenantDatabases.ACME, () -> scope.of(user).departmentIds()))
                .containsExactly(itDepartment);
        assertThat(visible(user)).containsExactly("ITS-1");
    }

    @Test
    void hrUserSeesOnlyHr_AC_006_2() {
        var user = user("HR Resolver", hrDepartment);
        assertThat(visible(user)).containsExactly("HR-1");
    }

    @Test
    void userWithoutDepartmentMembershipOrTicketsSeesNothing_AC_006_13() {
        var user = user("No Department", null);
        assertThat(tenants.runIn(TwoTenantDatabases.ACME, () -> scope.of(user).departmentIds()))
                .isEmpty();
        assertThat(visible(user)).isEmpty();
    }

    @Test
    void managerSeesNonPublishedTicketsOfEveryManagedProject_AC_006_2() {
        var user = user("Manager", null);
        UUID second = project(hrDepartment, "HRSECOND");
        ticket(second, "HRSECOND-1", "REJECTED", "Other", null);
        for (UUID project : Set.of(hrProject, second)) {
            jdbc.update(
                    "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,'PROJECT_MANAGER')",
                    user.id(),
                    project);
        }
        jdbc.update(
                "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,'MEMBER')",
                user.id(),
                itProject);
        assertThat(tenants.runIn(TwoTenantDatabases.ACME, () -> scope.of(user).managedProjectIds()))
                .containsExactlyInAnyOrder(internalProjectId(hrProject), internalProjectId(second));
        assertThat(visible(user)).containsExactlyInAnyOrder("HR-2", "HRSECOND-1");
    }

    @Test
    void writerSeesOwnPendingTicketAcrossDepartments_AC_006_2() {
        var user = user("Solution Writer", null);
        ticket(hrProject, "OWN-1", "PENDING_REVIEW", "Imported author text", user.id());
        assertThat(tenants.runIn(TwoTenantDatabases.ACME, () -> scope.of(user).writerId()))
                .contains(user.id());
        assertThat(visible(user)).containsExactly("OWN-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REMOVED", "INACTIVE"})
    void managedRemovedProjectIsNotManaged_AC_006_2(String status) {
        var user = user("Manager", null);
        jdbc.update(
                "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,'PROJECT_MANAGER')",
                user.id(),
                hrProject);
        jdbc.update("UPDATE project SET status = ? WHERE public_id = ?", status, hrProject);

        assertThat(tenants.runIn(TwoTenantDatabases.ACME, () -> scope.of(user).managedProjectIds()))
                .isEmpty();
        assertThat(visible(user)).isEmpty();
    }

    @Test
    void sameNameUsersDoNotShareTickets_AC_006_2() {
        var first = user("Same Name", null);
        var second = user("Same Name", null);
        ticket(hrProject, "FIRST-1", "PENDING_REVIEW", first.fullName(), first.id());
        ticket(hrProject, "SECOND-1", "PENDING_REVIEW", second.fullName(), second.id());
        ticket(hrProject, "IMPORTED-1", "PENDING_REVIEW", first.fullName(), null);

        assertThat(visible(first)).containsExactly("FIRST-1");
        assertThat(visible(second)).containsExactly("SECOND-1");
    }

    @Test
    void inactiveDepartmentIsExcluded_AC_006_2_AC_006_13() {
        long deleted = department("Former", "DELETED");
        var user = user("Former Resolver", deleted);
        ticket(project(deleted, "OLD"), "OLD-1", "PUBLISHED", "Other", null);
        assertThat(tenants.runIn(TwoTenantDatabases.ACME, () -> scope.of(user).departmentIds()))
                .isEmpty();
        assertThat(visible(user)).isEmpty();
    }

    @Test
    void readsMembershipOnlyFromBoundWorkspace_AC_006_2_AC_006_13() {
        var user = user("Tenant A Resolver", itDepartment);
        var result = tenants.runIn(TwoTenantDatabases.BETA, () -> scope.of(user));
        assertThat(result.departmentIds()).isEmpty();
        assertThat(result.managedProjectIds()).isEmpty();
        assertThat(result.writerId()).contains(user.id());
    }

    private List<String> visible(TSolveUser user) {
        return tenants.runIn(
                TwoTenantDatabases.ACME,
                () -> tickets.list(user, new TicketListQuery(null, null, null, null, null, null, null)).stream()
                        .map(TicketSummaryResponse::sourceTicketId)
                        .toList());
    }

    private TSolveUser user(String name, Long department) {
        UUID id = UUID.randomUUID();
        String email = id + "@example.test";
        jdbc.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at,department_id) VALUES (?,?,?,'ACTIVE',now(),?)",
                id,
                email,
                name,
                department);
        return new TSolveUser(id, email, name, Set.of(Role.TICKET_RESOLVER, Role.PROJECT_MANAGER));
    }

    private long department(String name, String status) {
        return jdbc.queryForObject(
                "INSERT INTO department(name,status) VALUES (?,?) RETURNING department_id", Long.class, name, status);
    }

    private UUID project(long department, String key) {
        return jdbc.queryForObject(
                "INSERT INTO project(department_id,jira_project_key) VALUES (?,?) RETURNING public_id",
                UUID.class,
                department,
                key);
    }

    private long internalProjectId(UUID id) {
        return jdbc.queryForObject("SELECT project_id FROM project WHERE public_id = ?", Long.class, id);
    }

    private void ticket(UUID project, String key, String status, String writer, UUID writerId) {
        jdbc.update("""
                INSERT INTO ticket(ticket_id,source_ticket_id,project_id,source,status,title,writer,writer_id,
                    symptoms,root_cause,resolution_steps,scope)
                VALUES (?, ?, ?, 'CSV', ?, 'Title', ?, ?, 'Symptoms', 'Root cause', 'Steps', 'Scope')
                """, UUID.randomUUID(), key, project, status, writer, writerId);
    }
}
