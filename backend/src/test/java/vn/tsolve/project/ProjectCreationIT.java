package vn.tsolve.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.project.ProjectApiITConfiguration.FakeTrackerProjectService;
import vn.tsolve.project.dto.CreateProjectRequest;
import vn.tsolve.project.service.ProjectService;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
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
@WithTSolveUser(
        id = "00000000-0000-007f-0000-000000000137",
        email = "pm@pcr-fixture.test",
        roles = Role.PROJECT_MANAGER)
class ProjectCreationIT {
    private static final UUID OWN = new UUID(127, 301);
    private static final UUID OTHER = new UUID(127, 302);
    private static final UUID CLOSED = new UUID(127, 303);
    private static final UUID CALLER = new UUID(127, 311);
    private static final UUID SECOND = new UUID(127, 312);
    private static final UUID FOREIGN = new UUID(127, 313);
    private static final String CALLER_EMAIL = "pm@pcr-fixture.test";
    private static final List<TrackerStatus> STATUSES = List.of(
            new TrackerStatus("10000", "To Do", false),
            new TrackerStatus("10001", "In Review", false),
            new TrackerStatus("10002", "Done", true));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private FakeTrackerProjectService tracker;

    @BeforeEach
    void seed() {
        tracker.reset();
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            db.update("DELETE FROM project_membership WHERE project_id IN"
                    + " (SELECT public_id FROM project WHERE jira_project_key LIKE 'PCR%')");
            db.update("DELETE FROM app_user WHERE email LIKE '%@pcr-fixture.test'");
            db.update("DELETE FROM project WHERE jira_project_key LIKE 'PCR%'");
            db.update("DELETE FROM department WHERE public_id IN (?, ?, ?)", OWN, OTHER, CLOSED);
            db.update(
                    "INSERT INTO department(public_id,name,status) VALUES (?, 'PCR Operations', 'ACTIVE'),"
                            + " (?, 'PCR Support', 'ACTIVE'), (?, 'PCR Closed', 'DEACTIVATED')",
                    OWN,
                    OTHER,
                    CLOSED);
            user(db, CALLER, OWN);
            user(db, SECOND, OWN);
            user(db, FOREIGN, OTHER);
        }
        for (String key : List.of("PCRNEW", "PCRCANON", "PCRRACE", "PCRFAIL", "PCRRECHK", "PCRSAME", "PCRDENY")) {
            tracker.stub(key, TrackerProjectLookup.found(key, STATUSES));
        }
        tracker.stub("PCRALIAS", TrackerProjectLookup.found("PCRCANON", STATUSES));
        tracker.stub("PCRRACEA", TrackerProjectLookup.found("PCRRACE", STATUSES));
    }

    @Test
    void insertsOneProjectMembershipAndAuditAndAnswers201_AC_002_8_AC_002_9_AC_002_10_AC_002_13_AC_002_15()
            throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        String response = perform(
                        TwoTenantDatabases.ACME,
                        CALLER,
                        "{\"jiraProjectKey\":\" pcrnew \",\"doneStatusIds\":[\"10001\",\"10002\",\"10001\"],"
                                + "\"reviewRange\":2147483647}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(response).get("projectId").asText());
        assertThat(mapper.readTree(response))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(Map.of(
                        "projectId",
                        id.toString(),
                        "departmentId",
                        OWN.toString(),
                        "jiraProjectKey",
                        "PCRNEW",
                        "doneStatusIds",
                        List.of("10001", "10002"),
                        "reviewRange",
                        2147483647,
                        "status",
                        "ACTIVE"))));

        Map<String, Object> row = db.queryForMap(
                "SELECT p.jira_project_key, p.review_range, p.status, p.field_mapping IS NULL AS no_mapping,"
                        + " p.done_status = CAST('[\"10001\",\"10002\"]' AS jsonb) AS ids_match,"
                        + " p.department_id = (SELECT department_id FROM department WHERE public_id = ?) AS own"
                        + " FROM project p WHERE p.public_id = ?",
                OWN,
                id);
        assertThat(row)
                .containsEntry("jira_project_key", "PCRNEW")
                .containsEntry("review_range", 2147483647)
                .containsEntry("status", "ACTIVE")
                .containsEntry("no_mapping", true)
                .containsEntry("ids_match", true)
                .containsEntry("own", true);
        assertThat(db.queryForList("SELECT user_id, role_in_project FROM project_membership WHERE project_id = ?", id))
                .containsExactly(Map.of("user_id", CALLER, "role_in_project", "PROJECT_MANAGER"));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", "CREATE");
        details.put("jiraProjectKey", "PCRNEW");
        details.put("departmentId", OWN.toString());
        details.put("doneStatusIds", List.of("10001", "10002"));
        details.put("reviewRange", 2147483647);
        List<Map<String, Object>> audits = db.queryForList(
                "SELECT actor_id, actor_email, action, target_type,"
                        + " details = CAST(? AS jsonb) AS details_match FROM audit_log_entry WHERE target_id = ?",
                mapper.writeValueAsString(details),
                id);
        assertThat(audits)
                .containsExactly(Map.of(
                        "actor_id",
                        CALLER,
                        "actor_email",
                        CALLER_EMAIL,
                        "action",
                        "TRACKER_CONFIGURATION_CHANGE",
                        "target_type",
                        "PROJECT",
                        "details_match",
                        true));
        assertThat(tracker.calls()).containsExactly("PCRNEW");
    }

    @Test
    void storesTheCanonicalKeyAndFindsTheCommittedProjectByIt_AC_002_8_AC_002_14() throws Exception {
        UUID id = created(perform(TwoTenantDatabases.ACME, CALLER, body("PCRALIAS"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jiraProjectKey").value("PCRCANON")));
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject("SELECT jira_project_key FROM project WHERE public_id = ?", String.class, id))
                .isEqualTo("PCRCANON");
        assertThat(scope.runIn(
                        TwoTenantDatabases.ACME, () -> projectService.findActivePublicIdByJiraProjectKey("PCRCANON")))
                .contains(id);
        assertThat(tracker.calls()).containsExactly("PCRALIAS");
    }

    @Test
    void writesProjectMembershipAndAuditInOneTransactionAfterJira_AC_002_18() throws Exception {
        UUID id =
                created(perform(TwoTenantDatabases.ACME, CALLER, body("PCRNEW")).andExpect(status().isCreated()));
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        String project = db.queryForObject("SELECT xmin::text FROM project WHERE public_id = ?", String.class, id);
        String membership =
                db.queryForObject("SELECT xmin::text FROM project_membership WHERE project_id = ?", String.class, id);
        String audit =
                db.queryForObject("SELECT xmin::text FROM audit_log_entry WHERE target_id = ?", String.class, id);
        assertThat(membership).isEqualTo(project);
        assertThat(audit).isEqualTo(project);
        assertThat(tracker.calls()).containsExactly("PCRNEW");
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-department", "other-department", "aliases"})
    void competingSavesCommitOneProjectAndAnswerOneConflict_AC_002_11(String variant) throws Exception {
        String keyA = "PCRRACE";
        String keyB = variant.equals("aliases") ? "PCRRACEA" : "PCRRACE";
        UUID callerB = variant.equals("other-department") ? FOREIGN : SECOND;
        var barrier = new CyclicBarrier(2);
        // Both Saves pass the local link check before either writes.
        tracker.beforeAnswer(() -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        int auditsBefore = auditCount();
        var start = new CountDownLatch(1);
        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<MockHttpServletResponse>> futures = List.of(
                    pool.submit(() -> race(start, CALLER, keyA)), pool.submit(() -> race(start, callerB, keyB)));
            start.countDown();
            for (var future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
        }
        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        Map<String, Object> winner = db.queryForMap(
                "SELECT p.public_id, d.name FROM project p JOIN department d ON d.department_id = p.department_id"
                        + " WHERE p.jira_project_key = 'PCRRACE'");
        UUID id = (UUID) winner.get("public_id");
        for (MockHttpServletResponse response : responses) {
            JsonNode body = mapper.readTree(response.getContentAsString());
            if (response.getStatus() == 409) {
                assertThat(body.get("code").asText()).isEqualTo("MSG49");
                assertThat(body.get("params").get("tracker_name").asText()).isEqualTo("Jira");
                assertThat(body.get("params").get("department_name").asText()).isEqualTo(winner.get("name"));
                assertThat(body.get("fieldErrors").get(0).get("field").asText()).isEqualTo("jiraProjectKey");
            } else {
                assertThat(body.get("projectId").asText()).isEqualTo(id.toString());
            }
        }
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM project WHERE jira_project_key LIKE 'PCRRACE%'", Integer.class))
                .isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM project_membership WHERE project_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM audit_log_entry WHERE target_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(auditCount()).isEqualTo(auditsBefore + 1);
        assertThat(tracker.calls()).hasSize(2);
    }

    @Test
    void rollsBackProjectAndMembershipWhenTheAuditFails_AC_002_12() {
        int auditsBefore = auditCount();
        // The caller is read before the Jira read; the real AuditService then finds no signed-in user.
        tracker.beforeAnswer(SecurityContextHolder::clearContext);
        assertThatThrownBy(() -> scope.runIn(
                        TwoTenantDatabases.ACME,
                        () -> projectService.create(
                                new CreateProjectRequest("PCRFAIL", List.of("10002"), BigDecimal.ONE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signed-in user");
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        assertThat(db.queryForObject("SELECT count(*) FROM project WHERE jira_project_key = 'PCRFAIL'", Integer.class))
                .isZero();
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM project_membership WHERE user_id = ?", Integer.class, CALLER))
                .isZero();
        assertThat(auditCount()).isEqualTo(auditsBefore);
        assertThat(tracker.calls()).containsExactly("PCRFAIL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"account", "role", "department-null", "department-inactive", "claimed"})
    void rechecksEligibilityAndLinkInsideTheWriteTransaction_AC_002_17(String change) throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        int auditsBefore = auditCount();
        tracker.beforeAnswer(() -> {
            switch (change) {
                case "account" -> db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", CALLER);
                case "role" ->
                    db.update("DELETE FROM user_role WHERE user_id = ? AND role = 'PROJECT_MANAGER'", CALLER);
                case "department-null" -> db.update("UPDATE app_user SET department_id = NULL WHERE id = ?", CALLER);
                case "department-inactive" ->
                    db.update(
                            "UPDATE app_user SET department_id = (SELECT department_id FROM department WHERE public_id = ?)"
                                    + " WHERE id = ?",
                            CLOSED,
                            CALLER);
                case "claimed" ->
                    db.update(
                            "INSERT INTO project(department_id, jira_project_key, status)"
                                    + " SELECT department_id, 'PCRRECHK', 'ACTIVE' FROM department WHERE public_id = ?",
                            OTHER);
                default -> throw new AssertionError(change);
            }
        });
        ResultActions result = perform(TwoTenantDatabases.ACME, CALLER, body("PCRRECHK"));
        if (change.equals("claimed")) {
            result.andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG49"))
                    .andExpect(jsonPath("$.params.department_name").value("PCR Support"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("jiraProjectKey"));
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MSG08"))
                    .andExpect(jsonPath("$.params").isMap())
                    .andExpect(jsonPath("$.params").isEmpty())
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        }
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM project WHERE jira_project_key = 'PCRRECHK' AND department_id ="
                                + " (SELECT department_id FROM department WHERE public_id = ?)",
                        Integer.class,
                        OWN))
                .isZero();
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM project_membership WHERE user_id = ?", Integer.class, CALLER))
                .isZero();
        assertThat(auditCount()).isEqualTo(auditsBefore);
        assertThat(tracker.calls()).containsExactly("PCRRECHK");
    }

    @Test
    void savesTheSameKeyInEachWorkspaceDatabaseOnly_AC_007_6() throws Exception {
        List<Map<String, Object>> main = snapshot(TwoTenantDatabases.main());
        perform(TwoTenantDatabases.ACME, CALLER, body("PCRSAME")).andExpect(status().isCreated());
        perform(TwoTenantDatabases.BETA, CALLER, body("PCRSAME")).andExpect(status().isCreated());
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            assertThat(db.queryForObject(
                            "SELECT count(*) FROM project WHERE jira_project_key = 'PCRSAME'", Integer.class))
                    .isEqualTo(1);
        }
        assertThat(snapshot(TwoTenantDatabases.main())).isEqualTo(main);
    }

    @Test
    void failsUnboundWithoutDefaultDatabaseOrJiraUse_AC_007_5() throws Exception {
        List<Map<String, Object>> main = snapshot(TwoTenantDatabases.main());
        mvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PCRNEW")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"))
                .andExpect(jsonPath("$.params").isMap())
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        assertThat(tracker.calls()).isEmpty();
        assertThat(snapshot(TwoTenantDatabases.main())).isEqualTo(main);
    }

    @Test
    void refusesAnInactiveOwnDepartmentBeforeJiraOrWrites_AC_007_4_AC_007_8() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("UPDATE department SET status = 'DEACTIVATED' WHERE public_id = ?", OWN);
        List<Map<String, Object>> before = snapshot(db);
        perform(TwoTenantDatabases.ACME, CALLER, body("PCRDENY"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        assertThat(tracker.calls()).isEmpty();
        assertThat(snapshot(db)).isEqualTo(before);
    }

    private MockHttpServletResponse race(CountDownLatch start, UUID caller, String key) throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("start timed out");
        }
        return perform(TwoTenantDatabases.ACME, caller, body(key)).andReturn().getResponse();
    }

    private ResultActions perform(UUID workspace, UUID caller, String json) {
        TSolveUser user = new TSolveUser(
                caller,
                caller.equals(CALLER) ? CALLER_EMAIL : caller + "@pcr-fixture.test",
                "PCR fixture",
                Set.of(Role.PROJECT_MANAGER));
        var token = UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities());
        return scope.runIn(workspace, () -> {
            try {
                return mvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .with(authentication(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private UUID created(ResultActions result) throws Exception {
        return UUID.fromString(mapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("projectId")
                .asText());
    }

    private String body(String key) {
        return "{\"jiraProjectKey\":\"" + key + "\",\"doneStatusIds\":[\"10002\"],\"reviewRange\":3}";
    }

    private int auditCount() {
        return TwoTenantDatabases.tenantA().queryForObject("SELECT count(*) FROM audit_log_entry", Integer.class);
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

    private void user(JdbcTemplate db, UUID id, UUID department) {
        db.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at,department_id)"
                        + " SELECT ?,?,'PCR fixture','ACTIVE',now(),department_id FROM department WHERE public_id=?",
                id,
                id + "@pcr-fixture.test",
                department);
        db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,'PROJECT_MANAGER',now())", id);
    }
}
