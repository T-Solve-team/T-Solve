package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.exception.ScopeAccessDeniedException;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.identity.signin.port.TenantScope;

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
class ScopeAccessIT {
    private static final UUID D1 = new UUID(118, 101);
    private static final UUID D2 = new UUID(118, 102);
    private static final UUID D3 = new UUID(118, 103);
    private static final UUID DX = new UUID(118, 104);
    private static final UUID S1 = new UUID(118, 201);
    private static final UUID S2 = new UUID(118, 202);
    private static final UUID S3 = new UUID(118, 203);
    private static final UUID SX = new UUID(118, 204);
    private static final UUID P1 = new UUID(118, 301);
    private static final UUID P2 = new UUID(118, 302);
    private static final UUID P3 = new UUID(118, 303);
    private static final UUID PR = new UUID(118, 304);
    private static final UUID PX = new UUID(118, 305);
    private static final UUID PD = new UUID(118, 306);
    private static final UUID PM = new UUID(118, 401);
    private static final UUID TR = new UUID(118, 402);
    private static final UUID KM = new UUID(118, 403);
    private static final UUID ADMIN = new UUID(118, 404);
    private static final UUID EMPLOYEE = new UUID(118, 405);
    private static final UUID FORMER = new UUID(118, 406);
    private static final UUID DEACTIVATED = new UUID(118, 407);
    private static final UUID BETA_DEPARTMENT = new UUID(118, 501);
    private static final UUID BETA_PROJECT = new UUID(118, 502);
    private static final UUID BETA_SPACE = new UUID(118, 503);
    private static final UUID BETA_ACCOUNT = new UUID(118, 504);

    @Autowired
    private ScopeAccessService service;

    @Autowired
    private TenantScope scope;

    @BeforeEach
    void seed() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update(
                    "DELETE FROM space_manager WHERE space_id IN (SELECT space_id FROM space WHERE public_id IN"
                            + " (?,?,?,?,?))",
                    S1,
                    S2,
                    S3,
                    SX,
                    BETA_SPACE);
            db.update("DELETE FROM space WHERE public_id IN (?,?,?,?,?)", S1, S2, S3, SX, BETA_SPACE);
            db.update("DELETE FROM app_user WHERE email LIKE '%@scope-fixture.test'");
            db.update("DELETE FROM project WHERE jira_project_key LIKE 'SCOPE-%'");
            db.update("DELETE FROM department WHERE public_id IN (?,?,?,?,?)", D1, D2, D3, DX, BETA_DEPARTMENT);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        department(db, D1, "ACTIVE");
        department(db, D2, "ACTIVE");
        department(db, D3, "ACTIVE");
        department(db, DX, "DELETED");
        space(db, S1, D1);
        space(db, S2, D2);
        space(db, S3, D3);
        space(db, SX, DX);
        project(db, P1, D1, "ACTIVE");
        project(db, P2, D2, "ACTIVE");
        project(db, P3, D3, "ACTIVE");
        project(db, PR, D1, "REMOVED");
        project(db, PX, DX, "ACTIVE");
        project(db, PD, D1, "ACTIVE");
        user(db, PM, D1, "ACTIVE", "PROJECT_MANAGER");
        user(db, TR, null, "ACTIVE", "TICKET_RESOLVER");
        user(db, KM, null, "ACTIVE", "KNOWLEDGE_MANAGER");
        user(db, ADMIN, D1, "ACTIVE", "ADMIN");
        user(db, EMPLOYEE, null, "ACTIVE");
        user(db, FORMER, D3, "ACTIVE", "TICKET_RESOLVER");
        user(db, DEACTIVATED, D1, "DEACTIVATED", "PROJECT_MANAGER");
        membership(db, PM, P1, "PROJECT_MANAGER");
        membership(db, PM, PR, "PROJECT_MANAGER");
        membership(db, TR, P1, "MEMBER");
        membership(db, TR, P2, "MEMBER");
        membership(db, TR, PX, "MEMBER");
        membership(db, ADMIN, P1, "MEMBER");
        membership(db, FORMER, P3, "PROJECT_MANAGER");
        membership(db, DEACTIVATED, PD, "PROJECT_MANAGER");
        manager(db, KM, S3);

        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        department(beta, BETA_DEPARTMENT, "ACTIVE");
        project(beta, BETA_PROJECT, BETA_DEPARTMENT, "ACTIVE");
        space(beta, BETA_SPACE, BETA_DEPARTMENT);
        user(beta, BETA_ACCOUNT, BETA_DEPARTMENT, "ACTIVE", "PROJECT_MANAGER");
        membership(beta, BETA_ACCOUNT, BETA_PROJECT, "PROJECT_MANAGER");
    }

    @Test
    void refusesAnotherDepartmentForProjectManager_AC_011_2() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(PM, D1)).isTrue();
            assertThat(service.canAccessDepartment(PM, D2)).isFalse();
            assertThat(service.canAccessDepartment(FORMER, D3)).isFalse();
            return null;
        });
    }

    @Test
    void countsOwnDepartmentAndProjectDepartmentsIndependently_AC_011_2() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("UPDATE app_user SET department_id = NULL WHERE id = ?", PM);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(PM, D1)).isTrue();
            return null;
        });
        db.update("DELETE FROM project_membership WHERE user_id = ?", PM);
        db.update(
                "UPDATE app_user SET department_id = (SELECT department_id FROM department WHERE public_id = ?) WHERE"
                        + " id = ?",
                D2,
                PM);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(PM, D2)).isTrue();
            assertThat(service.canAccessDepartment(PM, D1)).isFalse();
            return null;
        });
    }

    @Test
    void grantsResolverEveryProjectDepartmentAndRefusesOthers_AC_011_3() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(TR, D1)).isTrue();
            assertThat(service.canAccessDepartment(TR, D2)).isTrue();
            assertThat(service.canAccessDepartment(TR, D3)).isFalse();
            return null;
        });
    }

    @Test
    void countsOnlyMembershipsBackedByCurrentRolesIncludingRemovedProjects_AC_011_4() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessProject(PM, P1)).isTrue();
            assertThat(service.canAccessProject(PM, PR)).isTrue();
            assertThat(service.canAccessProject(PM, P2)).isFalse();
            assertThat(service.canAccessProject(TR, P2)).isTrue();
            assertThat(service.canAccessProject(TR, P3)).isFalse();
            assertThat(service.canAccessProject(FORMER, P3)).isFalse();
            return null;
        });
    }

    @Test
    void grantsSpaceThroughDepartmentOrCurrentKnowledgeManagerRole_AC_011_5() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessSpace(PM, S1)).isTrue();
            assertThat(service.canAccessSpace(PM, S2)).isFalse();
            assertThat(service.canAccessSpace(TR, S1)).isTrue();
            assertThat(service.canAccessSpace(TR, S2)).isTrue();
            assertThat(service.canAccessSpace(TR, S3)).isFalse();
            assertThat(service.canAccessSpace(KM, S3)).isTrue();
            assertThat(service.canAccessSpace(KM, S1)).isFalse();
            assertThat(service.canAccessDepartment(KM, D3)).isFalse();
            assertThat(service.canAccessProject(KM, P3)).isFalse();
            return null;
        });
        TwoTenantDatabases.tenantA().update("DELETE FROM user_role WHERE user_id = ?", KM);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessSpace(KM, S3)).isFalse();
            return null;
        });
    }

    @Test
    void combinesKnowledgeManagerAndResolverScopes_AC_011_5() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,'TICKET_RESOLVER',now())", KM);
        membership(db, KM, P1, "MEMBER");
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessSpace(KM, S1)).isTrue();
            assertThat(service.canAccessSpace(KM, S3)).isTrue();
            assertThat(service.canAccessSpace(KM, S2)).isFalse();
            return null;
        });
    }

    @Test
    void grantsNothingToAdminOrEmployeeAlone_AC_011_6() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            for (UUID account : List.of(ADMIN, EMPLOYEE)) {
                assertThat(service.canAccessDepartment(account, D1)).isFalse();
                assertThat(service.canAccessProject(account, P1)).isFalse();
                assertThat(service.canAccessSpace(account, S1)).isFalse();
            }
            return null;
        });
    }

    @Test
    void refusesInactiveAccountsAndDeletedDepartments_AC_011_7() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        manager(db, KM, SX);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(DEACTIVATED, D1)).isFalse();
            assertThat(service.canAccessProject(DEACTIVATED, PD)).isFalse();
            assertThat(service.canAccessSpace(DEACTIVATED, S1)).isFalse();
            assertThat(service.canAccessDepartment(TR, DX)).isFalse();
            assertThat(service.canAccessProject(TR, PX)).isFalse();
            assertThat(service.canAccessSpace(TR, SX)).isFalse();
            assertThat(service.canAccessSpace(KM, SX)).isFalse();
            return null;
        });
    }

    @Test
    void refusesHiddenSpaceDespiteAccessibleDepartmentOrManager_AC_011_7() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("UPDATE space SET status = 'HIDDEN' WHERE public_id IN (?,?)", S1, S3);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(PM, D1)).isTrue();
            assertThat(service.canAccessSpace(PM, S1)).isFalse();
            assertThat(service.canAccessSpace(TR, S1)).isFalse();
            assertThat(service.canAccessSpace(KM, S3)).isFalse();
            return null;
        });
    }

    @Test
    void refusesStaleResolverMembershipsWithoutResolverRole_AC_011_4() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("DELETE FROM user_role WHERE user_id = ?", TR);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(TR, D1)).isFalse();
            assertThat(service.canAccessProject(TR, P1)).isFalse();
            assertThat(service.canAccessSpace(TR, S1)).isFalse();
            return null;
        });
    }

    @Test
    void refusesUnknownAccountsAndTargetsWithoutThrowing_AC_011_8() {
        UUID unknown = UUID.randomUUID();
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(unknown, D1)).isFalse();
            assertThat(service.canAccessProject(unknown, P1)).isFalse();
            assertThat(service.canAccessSpace(unknown, S1)).isFalse();
            assertThat(service.canAccessDepartment(PM, unknown)).isFalse();
            assertThat(service.canAccessProject(PM, unknown)).isFalse();
            assertThat(service.canAccessSpace(PM, unknown)).isFalse();
            return null;
        });
    }

    @Test
    void readsOnlyBoundWorkspaceAndRejectsUnboundCalls_AC_011_9() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            assertThat(service.canAccessDepartment(BETA_ACCOUNT, BETA_DEPARTMENT))
                    .isFalse();
            assertThat(service.canAccessProject(BETA_ACCOUNT, BETA_PROJECT)).isFalse();
            assertThat(service.canAccessSpace(BETA_ACCOUNT, BETA_SPACE)).isFalse();
            return null;
        });
        scope.runIn(TwoTenantDatabases.BETA, () -> {
            assertThat(service.canAccessDepartment(BETA_ACCOUNT, BETA_DEPARTMENT))
                    .isTrue();
            assertThat(service.canAccessProject(BETA_ACCOUNT, BETA_PROJECT)).isTrue();
            assertThat(service.canAccessSpace(BETA_ACCOUNT, BETA_SPACE)).isTrue();
            return null;
        });
        assertThatThrownBy(() -> service.canAccessProject(PM, P1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresRefusedAndUnknownProjectsWithSameMessage_AC_011_10() {
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            for (UUID project : List.of(P2, UUID.randomUUID())) {
                assertThatThrownBy(() -> service.requireProject(PM, project))
                        .isInstanceOfSatisfying(ScopeAccessDeniedException.class, error -> {
                            assertThat(error.getCode()).isEqualTo("MSG08");
                            assertThat(error.getStatus().value()).isEqualTo(403);
                        });
            }
            return null;
        });
    }

    @Test
    void leavesDataAndAuditUnchangedAcrossAllChecks_AC_011_11() {
        List<Long> beforeA = counts(TwoTenantDatabases.tenantA());
        List<Long> beforeB = counts(TwoTenantDatabases.tenantB());
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            for (UUID account : List.of(PM, TR, KM, ADMIN, EMPLOYEE, FORMER, DEACTIVATED, BETA_ACCOUNT)) {
                for (UUID department : List.of(D1, D2, D3, DX, BETA_DEPARTMENT)) {
                    service.canAccessDepartment(account, department);
                }
                for (UUID project : List.of(P1, P2, P3, PR, PX, PD, BETA_PROJECT)) {
                    service.canAccessProject(account, project);
                }
                for (UUID space : List.of(S1, S2, S3, SX, BETA_SPACE)) {
                    service.canAccessSpace(account, space);
                }
            }
            service.requireDepartment(PM, D1);
            service.requireProject(PM, P1);
            service.requireSpace(PM, S1);
            assertThatThrownBy(() -> service.requireDepartment(PM, D2)).isInstanceOf(ScopeAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireProject(PM, P2)).isInstanceOf(ScopeAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireSpace(PM, S2)).isInstanceOf(ScopeAccessDeniedException.class);
            return null;
        });
        assertThat(counts(TwoTenantDatabases.tenantA())).isEqualTo(beforeA);
        assertThat(counts(TwoTenantDatabases.tenantB())).isEqualTo(beforeB);
    }

    private List<Long> counts(JdbcTemplate db) {
        return List.of("app_user", "user_role", "project_membership", "space_manager", "audit_log_entry").stream()
                .map(table -> db.queryForObject("SELECT count(*) FROM " + table, Long.class))
                .toList();
    }

    private void department(JdbcTemplate db, UUID id, String status) {
        db.update("INSERT INTO department(public_id,name,status) VALUES (?,?,?)", id, "Scope " + id, status);
    }

    private void project(JdbcTemplate db, UUID id, UUID department, String status) {
        db.update(
                "INSERT INTO project(public_id,department_id,jira_project_key,status) SELECT ?,department_id,?,? FROM"
                        + " department WHERE public_id = ?",
                id,
                "SCOPE-" + id.getLeastSignificantBits(),
                status,
                department);
    }

    private void space(JdbcTemplate db, UUID id, UUID department) {
        db.update(
                "INSERT INTO space(public_id,department_id,name,status) SELECT ?,department_id,?,'ACTIVE' FROM"
                        + " department WHERE public_id = ?",
                id,
                "Scope " + id,
                department);
    }

    private void user(JdbcTemplate db, UUID id, UUID department, String status, String... roles) {
        db.update(
                "INSERT INTO app_user(id,email,status,created_at,department_id) VALUES (?,?,?,now(),(SELECT"
                        + " department_id FROM department WHERE public_id = ?))",
                id,
                id + "@scope-fixture.test",
                status,
                department);
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

    private void manager(JdbcTemplate db, UUID account, UUID space) {
        db.update(
                "INSERT INTO space_manager(space_id,user_id,assigned_at) SELECT space_id,?,now() FROM space WHERE"
                        + " public_id = ?",
                account,
                space);
    }
}
