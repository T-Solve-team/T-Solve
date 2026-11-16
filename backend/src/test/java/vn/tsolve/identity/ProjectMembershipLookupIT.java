package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.common.session.dto.ProjectMembership;
import vn.tsolve.common.session.dto.ProjectRole;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.identity.signin.port.TenantScope;

/** BUG-299 (KAN-342): {@code /me} got no Projects because no bean implemented the lookup (be-foundation AC-003.2). */
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
class ProjectMembershipLookupIT {
    private static final UUID D1 = new UUID(342, 101);
    private static final UUID D2 = new UUID(342, 102);
    private static final UUID SAM = new UUID(342, 301);
    private static final UUID ALPHA = new UUID(342, 302);
    private static final UUID REMOVED = new UUID(342, 303);
    private static final UUID OTHER = new UUID(342, 304);
    private static final UUID PM = new UUID(342, 401);
    private static final UUID TR = new UUID(342, 402);
    private static final UUID PM_WITHOUT_ROLE = new UUID(342, 403);
    private static final UUID MEMBER_WITHOUT_ROLE = new UUID(342, 404);
    private static final UUID BETA_DEPARTMENT = new UUID(342, 501);
    private static final UUID BETA_PROJECT = new UUID(342, 502);
    private static final UUID BETA_PM = new UUID(342, 503);

    @Autowired
    private ProjectMembershipLookup lookup;

    @Autowired
    private TenantScope scope;

    @BeforeEach
    void seed() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update("DELETE FROM app_user WHERE email LIKE '%@pmlk-fixture.test'");
            db.update("DELETE FROM project WHERE public_id IN (?,?,?,?,?)", SAM, ALPHA, REMOVED, OTHER, BETA_PROJECT);
            db.update("DELETE FROM department WHERE public_id IN (?,?,?)", D1, D2, BETA_DEPARTMENT);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        department(db, D1, "IT");
        department(db, D2, "HR");
        project(db, SAM, D1, "PMLK-SAM1", "ACTIVE");
        project(db, ALPHA, D2, "pmlk-alpha", "ACTIVE");
        project(db, REMOVED, D1, "PMLK-OLD", "REMOVED");
        project(db, OTHER, D2, "PMLK-ZZZ", "ACTIVE");
        user(db, PM, "PROJECT_MANAGER");
        user(db, TR, "TICKET_RESOLVER");
        user(db, PM_WITHOUT_ROLE);
        user(db, MEMBER_WITHOUT_ROLE, "PROJECT_MANAGER");
        membership(db, PM, SAM, "PROJECT_MANAGER");
        membership(db, PM, ALPHA, "PROJECT_MANAGER");
        membership(db, PM, REMOVED, "PROJECT_MANAGER");
        membership(db, TR, SAM, "MEMBER");
        membership(db, PM_WITHOUT_ROLE, OTHER, "PROJECT_MANAGER");
        membership(db, MEMBER_WITHOUT_ROLE, ALPHA, "MEMBER");

        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        department(beta, BETA_DEPARTMENT, "Beta IT");
        project(beta, BETA_PROJECT, BETA_DEPARTMENT, "PMLK-BETA", "ACTIVE");
        user(beta, BETA_PM, "PROJECT_MANAGER");
        membership(beta, BETA_PM, BETA_PROJECT, "PROJECT_MANAGER");
    }

    @Test
    void returnsTheProjectsOfAProjectManagerByJiraKey_BUG_299() {
        List<ProjectMembership> projects = scope.runIn(TwoTenantDatabases.ACME, () -> lookup.findProjects(PM));

        assertThat(projects)
                .containsExactly(
                        new ProjectMembership(ALPHA, "pmlk-alpha", D2, "HR", ProjectRole.PROJECT_MANAGER),
                        new ProjectMembership(REMOVED, "PMLK-OLD", D1, "IT", ProjectRole.PROJECT_MANAGER),
                        new ProjectMembership(SAM, "PMLK-SAM1", D1, "IT", ProjectRole.PROJECT_MANAGER));
    }

    @Test
    void returnsTheProjectsOfATicketResolverAsMember_BUG_299() {
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> lookup.findProjects(TR)))
                .containsExactly(new ProjectMembership(SAM, "PMLK-SAM1", D1, "IT", ProjectRole.MEMBER));
    }

    @Test
    void leavesOutARowWhoseRoleTheAccountDoesNotHold_BUG_299() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(lookup.findProjects(PM_WITHOUT_ROLE)).isEmpty();
            assertThat(lookup.findProjects(MEMBER_WITHOUT_ROLE)).isEmpty();
            return null;
        });
    }

    @Test
    void readsOnlyTheBoundWorkspace_BUG_299() {
        assertThat(scope.runIn(TwoTenantDatabases.ACME, () -> lookup.findProjects(BETA_PM)))
                .isEmpty();
        assertThat(scope.runIn(TwoTenantDatabases.BETA, () -> lookup.findProjects(BETA_PM)))
                .containsExactly(new ProjectMembership(
                        BETA_PROJECT, "PMLK-BETA", BETA_DEPARTMENT, "Beta IT", ProjectRole.PROJECT_MANAGER));
    }

    private void department(JdbcTemplate db, UUID id, String name) {
        db.update("INSERT INTO department(public_id,name,status) VALUES (?,?,'ACTIVE')", id, name);
    }

    private void project(JdbcTemplate db, UUID id, UUID department, String key, String status) {
        db.update(
                "INSERT INTO project(public_id,department_id,jira_project_key,status) SELECT ?,department_id,?,? FROM"
                        + " department WHERE public_id = ?",
                id,
                key,
                status,
                department);
    }

    private void user(JdbcTemplate db, UUID id, String... roles) {
        db.update(
                "INSERT INTO app_user(id,email,status,created_at) VALUES (?,?,'ACTIVE',now())",
                id,
                id + "@pmlk-fixture.test");
        for (String role : roles) {
            db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,?,now())", id, role);
        }
    }

    private void membership(JdbcTemplate db, UUID account, UUID project, String role) {
        db.update(
                "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,?)",
                account,
                project,
                role);
    }
}
