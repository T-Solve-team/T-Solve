package vn.tsolve.ticket.service.impl;

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
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * BUG-300 (KAN-343): with no {@code TSOLVE_STUB_PROJECTS} (staging, production) the import asks {@code
 * project_membership}; before, {@link StubProjectAccess} refused every Project Manager with MSG08.
 */
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
class MembershipProjectAccessIT {
    private static final UUID IT = new UUID(343, 101);
    private static final UUID GONE = new UUID(343, 102);
    private static final UUID DP = new UUID(343, 301);
    private static final UUID REMOVED = new UUID(343, 302);
    private static final UUID IN_GONE_DEPARTMENT = new UUID(343, 303);
    private static final UUID FORMER_PROJECT = new UUID(343, 304);
    private static final UUID DEACTIVATED_PROJECT = new UUID(343, 305);
    private static final UUID MISSING = new UUID(343, 399);
    private static final UUID PM = new UUID(343, 401);
    private static final UUID TR = new UUID(343, 402);
    private static final UUID FORMER_PM = new UUID(343, 403);
    private static final UUID DEACTIVATED = new UUID(343, 404);
    private static final UUID BETA_DEPARTMENT = new UUID(343, 501);
    private static final UUID BETA_PROJECT = new UUID(343, 502);
    private static final UUID BETA_PM = new UUID(343, 503);

    @Autowired
    private ProjectAccess access;

    @Autowired
    private TenantScope scope;

    @BeforeEach
    void seed() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update("DELETE FROM app_user WHERE email LIKE '%@pa-fixture.test'");
            db.update(
                    "DELETE FROM project WHERE public_id IN (?,?,?,?,?,?)",
                    DP,
                    REMOVED,
                    IN_GONE_DEPARTMENT,
                    FORMER_PROJECT,
                    DEACTIVATED_PROJECT,
                    BETA_PROJECT);
            db.update("DELETE FROM department WHERE public_id IN (?,?,?)", IT, GONE, BETA_DEPARTMENT);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        department(db, IT, "ACTIVE");
        department(db, GONE, "DELETED");
        project(db, DP, IT, "PA-DP", "ACTIVE");
        project(db, REMOVED, IT, "PA-OLD", "REMOVED");
        project(db, IN_GONE_DEPARTMENT, GONE, "PA-GONE", "ACTIVE");
        user(db, PM, "ACTIVE", "PROJECT_MANAGER");
        user(db, TR, "ACTIVE", "TICKET_RESOLVER");
        user(db, FORMER_PM, "ACTIVE");
        user(db, DEACTIVATED, "DEACTIVATED", "PROJECT_MANAGER");
        for (UUID project : List.of(DP, REMOVED, IN_GONE_DEPARTMENT)) {
            membership(db, PM, project, "PROJECT_MANAGER");
        }
        membership(db, TR, DP, "MEMBER");
        project(db, FORMER_PROJECT, IT, "PA-FORMER", "ACTIVE");
        project(db, DEACTIVATED_PROJECT, IT, "PA-DEACT", "ACTIVE");
        membership(db, FORMER_PM, FORMER_PROJECT, "PROJECT_MANAGER");
        membership(db, DEACTIVATED, DEACTIVATED_PROJECT, "PROJECT_MANAGER");

        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        department(beta, BETA_DEPARTMENT, "ACTIVE");
        project(beta, BETA_PROJECT, BETA_DEPARTMENT, "PA-BETA", "ACTIVE");
        user(beta, BETA_PM, "ACTIVE", "PROJECT_MANAGER");
        membership(beta, BETA_PM, BETA_PROJECT, "PROJECT_MANAGER");
    }

    @Test
    void letsTheProjectManagerOfAnActiveProjectImport_BUG_300() {
        assertThat(acme(() -> access.managesActive(PM.toString(), DP))).isTrue();
    }

    @Test
    void refusesAMemberAnInactiveAccountAndAnyoneWithoutTheRole_BUG_300() {
        acme(() -> {
            assertThat(access.managesActive(TR.toString(), DP)).isFalse();
            // a PROJECT_MANAGER row counts only with the role, as identity's scope check does
            assertThat(access.managesActive(FORMER_PM.toString(), FORMER_PROJECT))
                    .isFalse();
            assertThat(access.managesActive(DEACTIVATED.toString(), DEACTIVATED_PROJECT))
                    .isFalse();
            assertThat(access.managesActive("not-a-uuid", DP)).isFalse();
            assertThat(access.managesActive(PM.toString(), null)).isFalse();
            return null;
        });
    }

    @Test
    void refusesARemovedProjectOrOneOfADeletedDepartment_BUG_300() {
        acme(() -> {
            assertThat(access.managesActive(PM.toString(), REMOVED)).isFalse();
            assertThat(access.managesActive(PM.toString(), IN_GONE_DEPARTMENT)).isFalse();
            assertThat(access.isActive(DP)).isTrue();
            assertThat(access.isActive(REMOVED)).isFalse();
            assertThat(access.isActive(IN_GONE_DEPARTMENT)).isFalse();
            assertThat(access.isActive(MISSING)).isFalse();
            return null;
        });
    }

    @Test
    void takesTheJiraKeyFromTheProject_BUG_300() {
        acme(() -> {
            assertThat(access.jiraProjectKey(DP)).contains("PA-DP");
            assertThat(access.jiraProjectKey(MISSING)).isEmpty();
            return null;
        });
    }

    @Test
    void readsOnlyTheBoundWorkspace_BUG_300() {
        assertThat(acme(() -> access.managesActive(BETA_PM.toString(), BETA_PROJECT)))
                .isFalse();
        assertThat(scope.runIn(TwoTenantDatabases.BETA, () -> access.managesActive(BETA_PM.toString(), BETA_PROJECT)))
                .isTrue();
    }

    private <T> T acme(java.util.function.Supplier<T> work) {
        return scope.runIn(TwoTenantDatabases.ACME, work);
    }

    private void department(JdbcTemplate db, UUID id, String status) {
        db.update("INSERT INTO department(public_id,name,status) VALUES (?,?,?)", id, "PA " + id, status);
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

    private void user(JdbcTemplate db, UUID id, String status, String... roles) {
        db.update(
                "INSERT INTO app_user(id,email,status,created_at) VALUES (?,?,?,now())",
                id,
                id + "@pa-fixture.test",
                status);
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
