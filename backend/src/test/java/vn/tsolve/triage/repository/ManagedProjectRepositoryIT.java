package vn.tsolve.triage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.triage.dto.HistoryQuery;
import vn.tsolve.triage.dto.ManagedProject;
import vn.tsolve.triage.dto.SuggestionResultSummary;
import vn.tsolve.triage.service.ManagedProjects;
import vn.tsolve.triage.service.SuggestionResultService;

/** Which Projects count as managed for the suggestion history: the import access rule, read on PostgreSQL. */
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
class ManagedProjectRepositoryIT {
    private static final UUID IT = new UUID(347, 101);
    private static final UUID GONE = new UUID(347, 102);
    private static final UUID SUP = new UUID(347, 201);
    private static final UUID NET = new UUID(347, 202);
    private static final UUID REMOVED = new UUID(347, 203);
    private static final UUID IN_GONE_DEPARTMENT = new UUID(347, 204);
    private static final UUID OF_DEACTIVATED = new UUID(347, 205);
    private static final UUID OF_NO_ROLE = new UUID(347, 206);
    private static final UUID PM = new UUID(347, 1);
    private static final UUID MEMBER = new UUID(347, 2);
    private static final UUID DEACTIVATED = new UUID(347, 3);
    private static final UUID NO_ROLE = new UUID(347, 4);
    private static final UUID BETA_DEPARTMENT = new UUID(347, 501);
    private static final UUID BETA_PROJECT = new UUID(347, 502);
    private static final List<UUID> PROJECTS =
            List.of(SUP, NET, REMOVED, IN_GONE_DEPARTMENT, OF_DEACTIVATED, OF_NO_ROLE, BETA_PROJECT);

    @Autowired
    private ManagedProjectRepository repository;

    @Autowired
    private ManagedProjects managedProjects;

    @Autowired
    private SuggestionResultService results;

    @Autowired
    private TenantScope scope;

    @BeforeEach
    void seed() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            for (UUID project : PROJECTS) {
                db.update("DELETE FROM suggestion_result WHERE project_id = ?", project);
                db.update("DELETE FROM project_membership WHERE project_id = ?", project);
                db.update("DELETE FROM project WHERE public_id = ?", project);
            }
            db.update("DELETE FROM app_user WHERE email LIKE '%@mp-fixture.test'");
            db.update("DELETE FROM department WHERE public_id IN (?,?,?)", IT, GONE, BETA_DEPARTMENT);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        department(db, IT, "ACTIVE");
        department(db, GONE, "DELETED");
        project(db, SUP, IT, "MPSUP", "ACTIVE");
        project(db, NET, IT, "MPNET", "ACTIVE");
        project(db, REMOVED, IT, "MPOLD", "REMOVED");
        project(db, IN_GONE_DEPARTMENT, GONE, "MPGONE", "ACTIVE");
        project(db, OF_DEACTIVATED, IT, "MPDEA", "ACTIVE");
        project(db, OF_NO_ROLE, IT, "MPNOR", "ACTIVE");
        user(db, PM, "ACTIVE", true);
        user(db, MEMBER, "ACTIVE", true);
        user(db, DEACTIVATED, "DEACTIVATED", true);
        user(db, NO_ROLE, "ACTIVE", false);
        for (UUID project : List.of(SUP, NET, REMOVED, IN_GONE_DEPARTMENT)) {
            membership(db, PM, project, "PROJECT_MANAGER");
        }
        membership(db, MEMBER, SUP, "MEMBER");
        membership(db, DEACTIVATED, OF_DEACTIVATED, "PROJECT_MANAGER");
        membership(db, NO_ROLE, OF_NO_ROLE, "PROJECT_MANAGER");

        // the same account manages a Project in BETA only: ACME must not see it
        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        department(beta, BETA_DEPARTMENT, "ACTIVE");
        project(beta, BETA_PROJECT, BETA_DEPARTMENT, "MPBETA", "ACTIVE");
        user(beta, PM, "ACTIVE", true);
        membership(beta, PM, BETA_PROJECT, "PROJECT_MANAGER");
    }

    @Test
    void returnsOnlyTheActiveProjectsInActiveDepartmentsByJiraKey_AC_003_1() {
        assertThat(acme(() -> repository.managedBy(PM)))
                .extracting(ManagedProject::publicId, ManagedProject::key)
                .containsExactly(tuple(NET.toString(), "MPNET"), tuple(SUP.toString(), "MPSUP"));
    }

    @Test
    void readsTheSignedInAccountInTheBoundWorkspaceOnly_AC_003_1() {
        assertThat(acme(() -> managedProjects.of(PM.toString())))
                .extracting(ManagedProject::key)
                .containsExactly("MPNET", "MPSUP");
        assertThat(scope.runIn(TwoTenantDatabases.BETA, () -> managedProjects.of(PM.toString())))
                .extracting(ManagedProject::key)
                .containsExactly("MPBETA");
    }

    @Test
    void listsTheSuggestionResultsOfAManagedProject_AC_003_1() {
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO suggestion_result (project_id, jira_issue_key, suggested_solution) VALUES (?, ?,"
                                + " 'Check the VPN.')",
                        SUP,
                        "MPSUP-1");
        assertThat(acme(() -> results.history(PM.toString(), new HistoryQuery(null, null, null, null, null, null, null))
                        .getContent()))
                .extracting(SuggestionResultSummary::issueKey)
                .containsExactly("MPSUP-1");
    }

    @Test
    void returnsNoneForAMemberADeactivatedAccountOrAnAccountWithoutTheRole_AC_003_3() {
        acme(() -> {
            assertThat(repository.managedBy(MEMBER)).isEmpty();
            assertThat(repository.managedBy(DEACTIVATED)).isEmpty();
            assertThat(repository.managedBy(NO_ROLE)).isEmpty();
            return null;
        });
    }

    @Test
    void returnsNoneForAUserThatIsNoAccountId_AC_003_3() {
        acme(() -> {
            assertThat(managedProjects.of("not-a-uuid")).isEmpty();
            assertThat(managedProjects.of(null)).isEmpty();
            return null;
        });
    }

    private <T> T acme(Supplier<T> work) {
        return scope.runIn(TwoTenantDatabases.ACME, work);
    }

    private void department(JdbcTemplate db, UUID id, String status) {
        db.update("INSERT INTO department(public_id,name,status) VALUES (?,?,?)", id, "MP " + id, status);
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

    private void user(JdbcTemplate db, UUID id, String status, boolean projectManager) {
        db.update(
                "INSERT INTO app_user(id,email,status,created_at) VALUES (?,?,?,now())",
                id,
                id + "@mp-fixture.test",
                status);
        if (projectManager) {
            db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,'PROJECT_MANAGER',now())", id);
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
