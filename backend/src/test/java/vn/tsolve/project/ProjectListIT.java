package vn.tsolve.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.ProjectApiITConfiguration.FakeTrackerProjectService;

/** Same properties and import as the other Project ITs, so the cached context is reused (D43). */
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
@Import(ProjectApiITConfiguration.class)
@WithTSolveUser(
        id = "00000000-0000-007f-0000-000000000511",
        email = "pm@pls-fixture.test",
        roles = Role.PROJECT_MANAGER)
class ProjectListIT {
    private static final UUID OWN = new UUID(127, 0x501);
    private static final UUID EMPTY = new UUID(127, 0x502);
    private static final UUID OTHER = new UUID(127, 0x503);
    private static final UUID CALLER = new UUID(127, 0x511);
    private static final UUID MOVED = new UUID(127, 0x512);
    private static final UUID GONE = new UUID(127, 0x513);
    private static final UUID MEMBER = new UUID(127, 0x514);
    private static final UUID LEFT = new UUID(127, 0x515);
    private static final UUID LONELY = new UUID(127, 0x516);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TenantScope scope;

    @Autowired
    private FakeTrackerProjectService tracker;

    private final Map<String, UUID> ids = new HashMap<>();

    @BeforeEach
    void seed() {
        tracker.reset();
        ids.clear();
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update("DELETE FROM project_membership WHERE project_id IN"
                    + " (SELECT public_id FROM project WHERE jira_project_key LIKE 'PLS%')");
            db.update("DELETE FROM app_user WHERE email LIKE '%@pls-fixture.test'");
            db.update("DELETE FROM project WHERE jira_project_key LIKE 'PLS%'");
            db.update("DELETE FROM department WHERE public_id IN (?, ?, ?)", OWN, EMPTY, OTHER);
            db.update(
                    "INSERT INTO department(public_id,name,status) VALUES (?, 'PLS Operations', 'ACTIVE'),"
                            + " (?, 'PLS Empty', 'ACTIVE'), (?, 'PLS Support', 'ACTIVE')",
                    OWN,
                    EMPTY,
                    OTHER);
            user(db, CALLER, OWN, "PLS Caller", true);
            user(db, MOVED, OTHER, "PLS Moved", false);
            user(db, GONE, OWN, "PLS Gone", true);
            user(db, MEMBER, OWN, "PLS Member", false);
            user(db, LEFT, OWN, "PLS Left", false);
            user(db, LONELY, EMPTY, "PLS Lonely", true);
        }
        JdbcTemplate acme = TwoTenantDatabases.tenantA();
        acme.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id IN (?, ?)", GONE, LEFT);
        // Inserted out of order; code-point order is PLS9, PLSA, PLSB, PLS_A.
        project(acme, "PLSB", OWN, "ACTIVE", MOVED, MEMBER);
        project(acme, "PLS_A", OWN, "REMOVED", null);
        project(acme, "PLSA", OWN, "REMOVED", GONE, MEMBER, CALLER);
        project(acme, "PLS9", OWN, "ACTIVE", null, LEFT);
        project(acme, "PLSOTHER", OTHER, "ACTIVE", MOVED);
    }

    @Test
    void
            listsTheOwnDepartmentProjectsByKeyWithActiveManagersAndAllMemberships_AC_004_1_AC_004_2_AC_004_3_AC_004_4_AC_004_5_AC_004_6()
                    throws Exception {
        String body = perform(TwoTenantDatabases.ACME, CALLER, list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(mapper.readTree(body)).isEqualTo(mapper.readTree("""
                {"department":{"departmentId":"%s","name":"PLS Operations"},
                 "projects":[
                  {"projectId":"%s","jiraProjectKey":"PLS9","projectManager":null,"memberCount":1,"status":"ACTIVE"},
                  {"projectId":"%s","jiraProjectKey":"PLSA","projectManager":null,"memberCount":3,"status":"REMOVED"},
                  {"projectId":"%s","jiraProjectKey":"PLSB",
                   "projectManager":{"userId":"%s","fullName":"PLS Moved"},"memberCount":2,"status":"ACTIVE"},
                  {"projectId":"%s","jiraProjectKey":"PLS_A","projectManager":null,"memberCount":0,"status":"REMOVED"}]}
                """.formatted(
                        OWN, ids.get("PLS9"), ids.get("PLSA"), ids.get("PLSB"), MOVED, ids.get("PLS_A"))));
    }

    @Test
    void readsWithoutJiraOrAnyPersistedChange_AC_004_8() throws Exception {
        List<Map<String, Object>> tenant = snapshot(TwoTenantDatabases.tenantA());
        List<Map<String, Object>> main = snapshot(TwoTenantDatabases.main());
        perform(TwoTenantDatabases.ACME, CALLER, list()).andExpect(status().isOk());
        assertThat(tracker.calls()).isEmpty();
        assertThat(snapshot(TwoTenantDatabases.tenantA())).isEqualTo(tenant);
        assertThat(snapshot(TwoTenantDatabases.main())).isEqualTo(main);
    }

    @Test
    void returnsTheDepartmentWithAnEmptyProjectList_AC_004_7() throws Exception {
        perform(TwoTenantDatabases.ACME, LONELY, list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.departmentId").value(EMPTY.toString()))
                .andExpect(jsonPath("$.department.name").value("PLS Empty"))
                .andExpect(jsonPath("$.projects").isArray())
                .andExpect(jsonPath("$.projects").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"account", "role", "department-null", "department-inactive"})
    void refusesAnIneligibleCallerWithoutReadingProjects_AC_007_4(String change) throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        switch (change) {
            case "account" -> db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", CALLER);
            case "role" -> db.update("DELETE FROM user_role WHERE user_id = ? AND role = 'PROJECT_MANAGER'", CALLER);
            case "department-null" -> db.update("UPDATE app_user SET department_id = NULL WHERE id = ?", CALLER);
            case "department-inactive" ->
                db.update("UPDATE department SET status = 'DEACTIVATED' WHERE public_id = ?", OWN);
            default -> throw new AssertionError(change);
        }
        List<Map<String, Object>> before = snapshot(db);
        perform(TwoTenantDatabases.ACME, CALLER, list())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"))
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.projects").doesNotExist());
        assertThat(tracker.calls()).isEmpty();
        assertThat(snapshot(db)).isEqualTo(before);
    }

    @Test
    void failsUnboundWithoutReadingAnyDatabase_AC_007_5() throws Exception {
        List<Map<String, Object>> main = snapshot(TwoTenantDatabases.main());
        mvc.perform(list())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.params").isMap())
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        assertThat(tracker.calls()).isEmpty();
        assertThat(snapshot(TwoTenantDatabases.main())).isEqualTo(main);
    }

    @Test
    void listsOnlyTheBoundWorkspaceDatabase_AC_007_6() throws Exception {
        UUID beta = project(TwoTenantDatabases.tenantB(), "PLSB", OWN, "ACTIVE", null);
        perform(TwoTenantDatabases.BETA, CALLER, list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].projectId").value(beta.toString()))
                .andExpect(jsonPath("$.projects[0].memberCount").value(0));
        perform(TwoTenantDatabases.ACME, CALLER, list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(4))
                .andExpect(jsonPath("$.projects[2].projectId")
                        .value(ids.get("PLSB").toString()));
    }

    @Test
    void ignoresDepartmentAndWorkspaceScopeFromTheRequest_AC_007_7() throws Exception {
        perform(
                        TwoTenantDatabases.ACME,
                        CALLER,
                        list().param("departmentId", OTHER.toString())
                                .param("workspaceId", TwoTenantDatabases.BETA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.departmentId").value(OWN.toString()))
                .andExpect(jsonPath("$.projects.length()").value(4));
    }

    private MockHttpServletRequestBuilder list() {
        return get("/api/v1/projects");
    }

    private ResultActions perform(UUID workspace, UUID caller, MockHttpServletRequestBuilder request) {
        TSolveUser user =
                new TSolveUser(caller, caller + "@pls-fixture.test", "PLS fixture", Set.of(Role.PROJECT_MANAGER));
        var token = UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities());
        return scope.runIn(workspace, () -> {
            try {
                return mvc.perform(request.with(authentication(token)));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    /** A Project with an optional manager row and MEMBER rows; the id is kept by key for the assertions. */
    private UUID project(JdbcTemplate db, String key, UUID department, String status, UUID manager, UUID... members) {
        UUID id = db.queryForObject(
                "INSERT INTO project(department_id, jira_project_key, done_status, review_range, status)"
                        + " SELECT department_id, ?, CAST('[\"10002\"]' AS jsonb), 3, ? FROM department"
                        + " WHERE public_id = ? RETURNING public_id",
                UUID.class,
                key,
                status,
                department);
        if (manager != null) {
            db.update(
                    "INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (?, ?, 'PROJECT_MANAGER')",
                    manager,
                    id);
        }
        for (UUID member : members) {
            db.update(
                    "INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (?, ?, 'MEMBER')",
                    member,
                    id);
        }
        ids.putIfAbsent(key, id);
        return id;
    }

    private List<Map<String, Object>> snapshot(JdbcTemplate db) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String table : List.of("department", "app_user", "user_role", "project", "project_membership")) {
            rows.addAll(db.queryForList("SELECT '" + table + "' AS table_name, row_to_json(t)::text AS data FROM "
                    + table + " t ORDER BY row_to_json(t)::text"));
        }
        rows.addAll(db.queryForList("SELECT 'audit_log_entry' AS table_name, row_to_json(a)::text AS data"
                + " FROM audit_log_entry a ORDER BY audit_log_entry_id"));
        return rows;
    }

    private void user(JdbcTemplate db, UUID id, UUID department, String name, boolean manager) {
        db.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at,department_id)"
                        + " SELECT ?,?,?,'ACTIVE',now(),department_id FROM department WHERE public_id=?",
                id,
                id + "@pls-fixture.test",
                name,
                department);
        if (manager) {
            db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,'PROJECT_MANAGER',now())", id);
        }
    }
}
