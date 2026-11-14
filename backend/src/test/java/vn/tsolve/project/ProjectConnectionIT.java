package vn.tsolve.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.ProjectApiITConfiguration.FakeTrackerProjectService;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerStatus;

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
@WithTSolveUser(id = "00000000-0000-007f-0000-00000000000b", roles = Role.PROJECT_MANAGER)
class ProjectConnectionIT {
    private static final UUID OWN = new UUID(127, 1);
    private static final UUID OTHER = new UUID(127, 2);
    private static final UUID CALLER = new UUID(127, 11);
    private static final UUID PROTECTED_PM = new UUID(127, 12);
    private static final UUID OLD = new UUID(127, 21);
    private static final UUID NULL_SETTINGS = new UUID(127, 22);
    private static final UUID PROTECTED_PROJECT = new UUID(127, 23);
    private static final UUID ACTIVE = new UUID(127, 24);
    private static final UUID BETA_NEW = new UUID(127, 25);
    private static final List<TrackerStatus> STATUSES = List.of(
            new TrackerStatus("10000", "To Do", false),
            new TrackerStatus("10001", "In Review", false),
            new TrackerStatus("10002", "Done", true));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private FakeTrackerProjectService tracker;

    @BeforeEach
    void seed() {
        tracker.reset();
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update("DELETE FROM project_membership WHERE project_id IN"
                    + " (SELECT public_id FROM project WHERE jira_project_key LIKE 'PCT%')");
            db.update("DELETE FROM app_user WHERE email LIKE '%@pct-fixture.test'");
            db.update("DELETE FROM project WHERE jira_project_key LIKE 'PCT%'");
            db.update("DELETE FROM department WHERE public_id IN (?, ?)", OWN, OTHER);
            db.update(
                    "INSERT INTO department(public_id,name,status) VALUES"
                            + " (?, 'PCT Operations', 'ACTIVE'), (?, 'PCT Support', 'ACTIVE')",
                    OWN,
                    OTHER);
            user(db, CALLER, "Caller");
            user(db, PROTECTED_PM, "Protected PM");
            project(db, OLD, OWN, "PCTOLD", "REMOVED", "[\"10001\",\"STALE\"]", 0, CALLER);
            project(db, NULL_SETTINGS, OWN, "PCTNUL", "REMOVED", null, null, CALLER);
            project(db, PROTECTED_PROJECT, OWN, "PCTPRO", "REMOVED", null, null, PROTECTED_PM);
            project(db, ACTIVE, OTHER, "PCTACT", "ACTIVE", null, null, null);
        }
        project(TwoTenantDatabases.tenantB(), BETA_NEW, OWN, "PCTNEW", "ACTIVE", null, null, CALLER);
        for (String key : List.of("PCTNEW", "PCTOLD", "PCTNUL")) {
            tracker.stub(key, TrackerProjectLookup.found(key, STATUSES));
        }
    }

    @Test
    void preservesProjectMembershipAndAuditForSuccessAndFailure_AC_001_18_AC_001_8() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        List<Map<String, Object>> before = snapshot(db);
        perform(TwoTenantDatabases.ACME, body("PCTNEW")).andExpect(status().isOk());
        assertThat(snapshot(db)).isEqualTo(before);
        perform(TwoTenantDatabases.ACME, body("PCTOLD")).andExpect(status().isOk());
        assertThat(snapshot(db)).isEqualTo(before);
        perform(TwoTenantDatabases.ACME, body("PCTMISS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG48"));
        assertThat(snapshot(db)).isEqualTo(before);
        tracker.stub("PCTDOWN", TrackerProjectLookup.failed(Outcome.UNAVAILABLE));
        perform(TwoTenantDatabases.ACME, body("PCTDOWN"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
        assertThat(snapshot(db)).isEqualTo(before);
        perform(TwoTenantDatabases.ACME, body("PCTACT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG49"));
        assertThat(snapshot(db)).isEqualTo(before);
        assertThat(tracker.calls()).containsExactly("PCTNEW", "PCTOLD", "PCTMISS", "PCTDOWN");
    }

    @Test
    void usesOnlyBoundWorkspaceForTheSameJiraKey_AC_007_6_AC_001_8() throws Exception {
        perform(TwoTenantDatabases.ACME, body("PCTNEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconnect").value(false));
        perform(TwoTenantDatabases.BETA, body("PCTNEW"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG49"))
                .andExpect(jsonPath("$.params.department_name").value("PCT Operations"));
        assertThat(tracker.calls()).containsExactly("PCTNEW");
    }

    @Test
    void failsUnboundWithoutDefaultDatabaseOrJiraUse_AC_007_5() throws Exception {
        List<Map<String, Object>> before = snapshot(TwoTenantDatabases.main());
        mvc.perform(post("/api/v1/projects/connection-test")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PCTNEW")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.params").isMap())
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        assertThat(tracker.calls()).isEmpty();
        assertThat(snapshot(TwoTenantDatabases.main())).isEqualTo(before);
    }

    @Test
    void readsStoredJsonbIdsAndPreservesZeroRange_AC_001_13_AC_001_14_AC_001_8() throws Exception {
        perform(TwoTenantDatabases.ACME, body("PCTOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconnect").value(true))
                .andExpect(jsonPath("$.projectId").value(OLD.toString()))
                .andExpect(jsonPath("$.doneStatusIds").value(new ArrayList<>(List.of("10001"))))
                .andExpect(jsonPath("$.reviewRange").value(0));
        assertThat(tracker.calls()).containsExactly("PCTOLD");
    }

    @Test
    void fallsBackOnlyForNullSettings_AC_001_13_AC_001_14_AC_001_8() throws Exception {
        perform(TwoTenantDatabases.ACME, body("PCTNUL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconnect").value(true))
                .andExpect(jsonPath("$.projectId").value(NULL_SETTINGS.toString()))
                .andExpect(jsonPath("$.doneStatusIds").value(new ArrayList<>(List.of("10002"))))
                .andExpect(jsonPath("$.reviewRange").value(3));
        assertThat(tracker.calls()).containsExactly("PCTNUL");
    }

    @Test
    void protectsActiveManagerAndAllowsReconnectAfterDeactivation_AC_001_7_AC_001_8() throws Exception {
        perform(TwoTenantDatabases.ACME, body("PCTPRO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG49"))
                .andExpect(jsonPath("$.params.department_name").value("PCT Operations"));
        assertThat(tracker.calls()).isEmpty();
        TwoTenantDatabases.tenantA().update("UPDATE app_user SET status='DEACTIVATED' WHERE id=?", PROTECTED_PM);
        tracker.stub("PCTPRO", TrackerProjectLookup.found("PCTPRO", STATUSES));
        perform(TwoTenantDatabases.ACME, body("PCTPRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconnect").value(true))
                .andExpect(jsonPath("$.projectId").value(PROTECTED_PROJECT.toString()));
        assertThat(tracker.calls()).containsExactly("PCTPRO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"department", "account", "role"})
    void rechecksDatabaseEligibilityDespiteSessionRole_AC_007_4(String change) throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        switch (change) {
            case "department" -> db.update("UPDATE department SET status='DEACTIVATED' WHERE public_id=?", OWN);
            case "account" -> db.update("UPDATE app_user SET status='DEACTIVATED' WHERE id=?", CALLER);
            case "role" -> db.update("DELETE FROM user_role WHERE user_id=? AND role='PROJECT_MANAGER'", CALLER);
            default -> throw new AssertionError(change);
        }
        perform(TwoTenantDatabases.ACME, body("PCTNEW"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"))
                .andExpect(jsonPath("$.params").isMap())
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        assertThat(tracker.calls()).isEmpty();
    }

    private ResultActions perform(UUID workspace, String json) {
        return scope.runIn(workspace, () -> {
            try {
                return mvc.perform(post("/api/v1/projects/connection-test")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private String body(String key) {
        return "{\"jiraProjectKey\":\"" + key + "\"}";
    }

    private List<Map<String, Object>> snapshot(JdbcTemplate db) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(db.queryForList(
                "SELECT 'project' AS table_name, row_to_json(p)::text AS data FROM project p ORDER BY project_id"));
        rows.addAll(db.queryForList("SELECT 'project_membership' AS table_name, row_to_json(m)::text AS data"
                + " FROM project_membership m ORDER BY project_id,user_id"));
        rows.addAll(db.queryForList("SELECT 'audit_log_entry' AS table_name, row_to_json(a)::text AS data"
                + " FROM audit_log_entry a ORDER BY audit_log_entry_id"));
        return rows;
    }

    private void user(JdbcTemplate db, UUID id, String name) {
        db.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at,department_id)"
                        + " SELECT ?,?,?,'ACTIVE',now(),department_id FROM department WHERE public_id=?",
                id,
                id + "@pct-fixture.test",
                name,
                OWN);
        db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,'PROJECT_MANAGER',now())", id);
    }

    private void project(
            JdbcTemplate db,
            UUID id,
            UUID department,
            String key,
            String state,
            String storedIds,
            Integer range,
            UUID manager) {
        db.update(
                "INSERT INTO project(public_id,department_id,jira_project_key,status,done_status,review_range)"
                        + " SELECT ?,department_id,?,?,CAST(? AS jsonb),CAST(? AS INT) FROM department WHERE public_id=?",
                id,
                key,
                state,
                storedIds,
                range,
                department);
        if (manager != null) {
            db.update(
                    "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,'PROJECT_MANAGER')",
                    manager,
                    id);
        }
    }
}
