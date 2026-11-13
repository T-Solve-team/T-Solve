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

/** Reconnecting a Removed Project on real PostgreSQL; same context as the other Project ITs (D43). */
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
        id = "00000000-0000-007f-0000-000000000411",
        email = "pm@prc-fixture.test",
        roles = Role.PROJECT_MANAGER)
class ProjectReconnectIT {
    private static final UUID OWN = new UUID(127, 0x401);
    private static final UUID OTHER = new UUID(127, 0x402);
    private static final UUID CALLER = new UUID(127, 0x411);
    private static final UUID SECOND = new UUID(127, 0x412);
    private static final UUID FOREIGN = new UUID(127, 0x413);
    private static final UUID OLD_PM = new UUID(127, 0x414);
    private static final UUID MEMBER = new UUID(127, 0x415);
    private static final String CALLER_EMAIL = "pm@prc-fixture.test";
    private static final String OWN_NAME = "PRC Operations";
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
            String fixtures = " (SELECT public_id FROM project WHERE jira_project_key LIKE 'PRC%')";
            db.update("DELETE FROM ticket WHERE project_id IN" + fixtures);
            db.update("DELETE FROM project_membership WHERE project_id IN" + fixtures);
            db.update("DELETE FROM app_user WHERE email LIKE '%@prc-fixture.test'");
            db.update("DELETE FROM project WHERE jira_project_key LIKE 'PRC%'");
            db.update("DELETE FROM department WHERE public_id IN (?, ?)", OWN, OTHER);
            db.update(
                    "INSERT INTO department(public_id,name,status) VALUES (?, ?, 'ACTIVE'), (?, 'PRC Support', 'ACTIVE')",
                    OWN,
                    OWN_NAME,
                    OTHER);
            user(db, CALLER, OWN, true);
            user(db, SECOND, OWN, true);
            user(db, FOREIGN, OTHER, true);
            user(db, OLD_PM, OWN, true);
            user(db, MEMBER, OWN, false);
        }
        for (String key : List.of("PRCROLD", "PRCRACE", "PRCRCANON", "PRCRSAME", "PRCRCHK", "PRCRFAIL", "PRCRSWAP")) {
            tracker.stub(key, TrackerProjectLookup.found(key, STATUSES));
        }
        tracker.stub("PRCRACEA", TrackerProjectLookup.found("PRCRACE", STATUSES));
        tracker.stub("PRCRALIAS", TrackerProjectLookup.found("PRCRCANON", STATUSES));
    }

    @Test
    void reconnectsForTheSamePreviousManagerKeepingEverything_AC_003_1_AC_003_2_AC_003_3_AC_003_5_AC_003_6_AC_003_10()
            throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = removed(db, "PRCROLD", OWN, CALLER);
        Map<String, Object> kept = kept(db, id);
        List<String> members = memberships(db, id);

        String response = perform(
                        TwoTenantDatabases.ACME,
                        CALLER,
                        "{\"jiraProjectKey\":\"prcrold\",\"doneStatusIds\":[\"10001\",\"10002\",\"10001\"],"
                                + "\"reviewRange\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(mapper.readTree(response))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(Map.of(
                        "projectId",
                        id.toString(),
                        "departmentId",
                        OWN.toString(),
                        "jiraProjectKey",
                        "PRCROLD",
                        "doneStatusIds",
                        List.of("10001", "10002"),
                        "reviewRange",
                        5,
                        "status",
                        "ACTIVE"))));

        assertThat(kept(db, id)).isEqualTo(kept);
        assertThat(memberships(db, id)).isEqualTo(members);
        assertThat(db.queryForMap(
                        "SELECT status, review_range, done_status = CAST('[\"10001\",\"10002\"]' AS jsonb) AS ids_match"
                                + " FROM project WHERE public_id = ?",
                        id))
                .containsEntry("status", "ACTIVE")
                .containsEntry("review_range", 5)
                .containsEntry("ids_match", true);
        assertThat(db.queryForObject("SELECT count(*) FROM ticket WHERE project_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertReconnectAudit(db, id, "PRCROLD", List.of("10001", "10002"), 5);
        assertThat(scope.runIn(
                        TwoTenantDatabases.ACME, () -> projectService.findActivePublicIdByJiraProjectKey("PRCROLD")))
                .contains(id);
        assertThat(tracker.calls()).containsExactly("PRCROLD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "deactivated", "moved", "role-removed", "caller-member"})
    void replacesAnIneligiblePreviousManagerKeepingOtherMembers_AC_003_4_AC_002_18(String previous) throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = removed(db, "PRCROLD", OWN, previous.equals("none") ? null : OLD_PM);
        switch (previous) {
            case "none" -> {}
            case "deactivated" -> db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", OLD_PM);
            case "moved" ->
                db.update(
                        "UPDATE app_user SET department_id = (SELECT department_id FROM department WHERE public_id = ?)"
                                + " WHERE id = ?",
                        OTHER,
                        OLD_PM);
            case "role-removed" -> db.update("DELETE FROM user_role WHERE user_id = ?", OLD_PM);
            case "caller-member" -> {
                db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", OLD_PM);
                db.update(
                        "INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (?, ?, 'MEMBER')",
                        CALLER,
                        id);
            }
            default -> throw new AssertionError(previous);
        }
        String member = db.queryForObject(
                "SELECT row_to_json(m)::text FROM project_membership m WHERE project_id = ? AND user_id = ?",
                String.class,
                id,
                MEMBER);

        perform(TwoTenantDatabases.ACME, CALLER, body("PRCROLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(id.toString()));

        assertThat(db.queryForList(
                        "SELECT user_id, role_in_project FROM project_membership WHERE project_id = ? ORDER BY user_id",
                        id))
                .containsExactly(
                        Map.of("user_id", CALLER, "role_in_project", "PROJECT_MANAGER"),
                        Map.of("user_id", MEMBER, "role_in_project", "MEMBER"));
        assertThat(db.queryForObject(
                        "SELECT row_to_json(m)::text FROM project_membership m WHERE project_id = ? AND user_id = ?",
                        String.class,
                        id,
                        MEMBER))
                .isEqualTo(member);
        // The Project update, the manager change and the audit entry commit in one transaction.
        String project = db.queryForObject("SELECT xmin::text FROM project WHERE public_id = ?", String.class, id);
        assertThat(db.queryForObject(
                        "SELECT xmin::text FROM project_membership WHERE project_id = ? AND user_id = ?",
                        String.class,
                        id,
                        CALLER))
                .isEqualTo(project);
        assertThat(db.queryForObject("SELECT xmin::text FROM audit_log_entry WHERE target_id = ?", String.class, id))
                .isEqualTo(project);
        assertReconnectAudit(db, id, "PRCROLD", List.of("10002"), 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"protected", "foreign", "active"})
    void refusesProtectedForeignOrActiveProjectsBeforeJiraOrWrites_AC_003_7(String variant) throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = removed(
                db, "PRCROLD", variant.equals("foreign") ? OTHER : OWN, variant.equals("protected") ? SECOND : null);
        if (variant.equals("active")) {
            db.update("UPDATE project SET status = 'ACTIVE' WHERE public_id = ?", id);
        }
        List<Map<String, Object>> before = snapshot(db);
        perform(TwoTenantDatabases.ACME, CALLER, body("PRCROLD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG49"))
                .andExpect(jsonPath("$.params.tracker_name").value("Jira"))
                .andExpect(jsonPath("$.params.department_name")
                        .value(variant.equals("foreign") ? "PRC Support" : OWN_NAME))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("jiraProjectKey"));
        assertThat(tracker.calls()).isEmpty();
        assertThat(snapshot(db)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-key", "alias", "alias-and-create"})
    void competingSavesCommitOneReconnectOrCreateAndRefuseTheOther_AC_003_8(String variant) throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        boolean create = variant.equals("alias-and-create");
        // alias-and-create: one Save reconnects PRCRALIAS onto Jira's PRCRCANON, the other creates PRCRCANON.
        UUID id = removed(db, create ? "PRCRALIAS" : "PRCRACE", OWN, null);
        String keyA = create ? "PRCRALIAS" : "PRCRACE";
        String keyB =
                switch (variant) {
                    case "alias" -> "PRCRACEA";
                    case "alias-and-create" -> "PRCRCANON";
                    default -> "PRCRACE";
                };
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
            List<Future<MockHttpServletResponse>> futures =
                    List.of(pool.submit(() -> race(start, CALLER, keyA)), pool.submit(() -> race(start, SECOND, keyB)));
            start.countDown();
            for (var future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
        }
        List<Integer> statuses =
                responses.stream().map(MockHttpServletResponse::getStatus).toList();
        assertThat(statuses).contains(409).hasSize(2);
        assertThat(statuses).filteredOn(code -> code != 409).singleElement().satisfies(code -> assertThat(code)
                .isIn(create ? List.of(200, 201) : List.of(200)));
        for (MockHttpServletResponse response : responses) {
            if (response.getStatus() == 409) {
                JsonNode problem = mapper.readTree(response.getContentAsString());
                assertThat(problem.get("code").asText()).isEqualTo("MSG49");
                assertThat(problem.get("params").get("department_name").asText())
                        .isEqualTo(OWN_NAME);
                assertThat(problem.get("fieldErrors").get(0).get("field").asText())
                        .isEqualTo("jiraProjectKey");
            }
        }
        List<UUID> active = db.queryForList(
                "SELECT public_id FROM project WHERE jira_project_key LIKE 'PRCR%' AND status = 'ACTIVE'", UUID.class);
        assertThat(active).hasSize(1);
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM project_membership WHERE project_id = ? AND role_in_project = 'PROJECT_MANAGER'",
                        Integer.class,
                        active.get(0)))
                .isEqualTo(1);
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM audit_log_entry WHERE target_id = ?", Integer.class, active.get(0)))
                .isEqualTo(1);
        assertThat(auditCount()).isEqualTo(auditsBefore + 1);
        if (!create) {
            assertThat(active).containsExactly(id);
            assertThat(db.queryForObject(
                            "SELECT count(*) FROM project_membership WHERE project_id = ? AND user_id = ?",
                            Integer.class,
                            id,
                            MEMBER))
                    .isEqualTo(1);
        }
        assertThat(tracker.calls()).hasSize(2);
    }

    @Test
    void restoresTheRemovedProjectAndMembersWhenTheAuditFails_AC_003_9() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = removed(db, "PRCRFAIL", OWN, OLD_PM);
        db.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", OLD_PM);
        db.update(
                "INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (?, ?, 'MEMBER')",
                CALLER,
                id);
        String project = projectRow(db, id);
        List<String> members = memberships(db, id);
        int auditsBefore = auditCount();
        // The caller is read before the Jira read; the real AuditService then finds no signed-in user, after the
        // conditional update, the old manager's removal and the caller's promotion.
        tracker.beforeAnswer(SecurityContextHolder::clearContext);
        assertThatThrownBy(() -> scope.runIn(
                        TwoTenantDatabases.ACME,
                        () -> projectService.create(
                                new CreateProjectRequest("PRCRFAIL", List.of("10002"), BigDecimal.ONE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signed-in user");
        assertThat(projectRow(db, id)).isEqualTo(project);
        assertThat(memberships(db, id)).isEqualTo(members).hasSize(3);
        assertThat(auditCount()).isEqualTo(auditsBefore);
        assertThat(tracker.calls()).containsExactly("PRCRFAIL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "claimed", "activated"})
    void rechecksEligibilityAndLinkBeforeReconnecting_AC_002_17_AC_003_1(String change) throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = removed(db, "PRCRCHK", OWN, null);
        int auditsBefore = auditCount();
        tracker.beforeAnswer(() -> {
            switch (change) {
                case "role" ->
                    db.update("DELETE FROM user_role WHERE user_id = ? AND role = 'PROJECT_MANAGER'", CALLER);
                case "claimed" ->
                    db.update(
                            "INSERT INTO project_membership(user_id, project_id, role_in_project)"
                                    + " VALUES (?, ?, 'PROJECT_MANAGER')",
                            SECOND,
                            id);
                case "activated" -> db.update("UPDATE project SET status = 'ACTIVE' WHERE public_id = ?", id);
                default -> throw new AssertionError(change);
            }
        });
        ResultActions result = perform(TwoTenantDatabases.ACME, CALLER, body("PRCRCHK"));
        if (change.equals("role")) {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MSG08"));
        } else {
            result.andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG49"))
                    .andExpect(jsonPath("$.params.department_name").value(OWN_NAME));
        }
        assertThat(db.queryForMap(
                        "SELECT status, review_range, done_status = CAST('[\"10000\"]' AS jsonb) AS ids_kept"
                                + " FROM project WHERE public_id = ?",
                        id))
                .containsEntry("status", change.equals("activated") ? "ACTIVE" : "REMOVED")
                .containsEntry("review_range", 9)
                .containsEntry("ids_kept", true);
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM project_membership WHERE project_id = ? AND user_id = ?",
                        Integer.class,
                        id,
                        CALLER))
                .isZero();
        assertThat(auditCount()).isEqualTo(auditsBefore);
        assertThat(tracker.calls()).containsExactly("PRCRCHK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unknown-id"})
    void refusesReconnectAfterAFailedFreshJiraCheck_AC_002_2_AC_002_3_AC_002_5_AC_003_1(String failure)
            throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        String key = failure.equals("missing") ? "PRCRMISS" : "PRCRSWAP";
        removed(db, key, OWN, CALLER);
        List<Map<String, Object>> before = snapshot(db);
        String json = failure.equals("missing")
                ? body(key)
                : "{\"jiraProjectKey\":\"" + key + "\",\"doneStatusIds\":[\"10009\"],\"reviewRange\":3}";
        ResultActions result = perform(TwoTenantDatabases.ACME, CALLER, json);
        if (failure.equals("missing")) {
            result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MSG48"));
        } else {
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG01"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("doneStatusIds"));
        }
        assertThat(snapshot(db)).isEqualTo(before);
        assertThat(tracker.calls()).containsExactly(key);
    }

    @Test
    void reconnectsOnlyInTheBoundWorkspaceDatabase_AC_007_6() throws Exception {
        UUID acme = removed(TwoTenantDatabases.tenantA(), "PRCRSAME", OWN, CALLER);
        UUID beta = removed(TwoTenantDatabases.tenantB(), "PRCRSAME", OWN, CALLER);
        String betaRow = projectRow(TwoTenantDatabases.tenantB(), beta);
        List<Map<String, Object>> main = snapshot(TwoTenantDatabases.main());
        perform(TwoTenantDatabases.ACME, CALLER, body("PRCRSAME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(acme.toString()));
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject("SELECT status FROM project WHERE public_id = ?", String.class, acme))
                .isEqualTo("ACTIVE");
        assertThat(projectRow(TwoTenantDatabases.tenantB(), beta)).isEqualTo(betaRow);
        assertThat(snapshot(TwoTenantDatabases.main())).isEqualTo(main);
    }

    private void assertReconnectAudit(JdbcTemplate db, UUID id, String key, List<String> ids, int range)
            throws Exception {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", "RECONNECT");
        details.put("jiraProjectKey", key);
        details.put("departmentId", OWN.toString());
        details.put("doneStatusIds", ids);
        details.put("reviewRange", range);
        assertThat(db.queryForList(
                        "SELECT actor_id, actor_email, action, target_type,"
                                + " details = CAST(? AS jsonb) AS details_match FROM audit_log_entry WHERE target_id = ?",
                        mapper.writeValueAsString(details),
                        id))
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
                caller.equals(CALLER) ? CALLER_EMAIL : caller + "@prc-fixture.test",
                "PRC fixture",
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

    private String body(String key) {
        return "{\"jiraProjectKey\":\"" + key + "\",\"doneStatusIds\":[\"10002\"],\"reviewRange\":3}";
    }

    /** A Removed Project with settings, a field mapping, a ticket, a MEMBER row and an optional previous manager. */
    private UUID removed(JdbcTemplate db, String key, UUID department, UUID manager) {
        UUID id = db.queryForObject(
                "INSERT INTO project(department_id, jira_project_key, done_status, review_range, status, field_mapping)"
                        + " SELECT department_id, ?, CAST('[\"10000\"]' AS jsonb), 9, 'REMOVED',"
                        + " CAST('{\"summary\":\"title\"}' AS jsonb) FROM department WHERE public_id = ?"
                        + " RETURNING public_id",
                UUID.class,
                key,
                department);
        if (manager != null) {
            db.update(
                    "INSERT INTO project_membership(user_id, project_id, role_in_project, date_added)"
                            + " VALUES (?, ?, 'PROJECT_MANAGER', '2026-01-01T00:00:00Z')",
                    manager,
                    id);
        }
        db.update(
                "INSERT INTO project_membership(user_id, project_id, role_in_project, date_added)"
                        + " VALUES (?, ?, 'MEMBER', '2026-01-02T00:00:00Z')",
                MEMBER,
                id);
        db.update("""
                INSERT INTO ticket(ticket_id, source_ticket_id, project_id, source, status, title,
                    symptoms, root_cause, resolution_steps, scope, updated_at)
                VALUES (?, ?, ?, 'CSV', 'PUBLISHED', 'PRC ticket', 'Symptoms', 'Root cause', 'Resolution', 'Scope',
                    '2026-11-01T00:00:00Z')
                """, UUID.randomUUID(), key + "-1", id);
        return id;
    }

    private Map<String, Object> kept(JdbcTemplate db, UUID id) {
        return db.queryForMap(
                "SELECT project_id, department_id, jira_project_key, field_mapping::text AS mapping"
                        + " FROM project WHERE public_id = ?",
                id);
    }

    private String projectRow(JdbcTemplate db, UUID id) {
        return db.queryForObject("SELECT row_to_json(p)::text FROM project p WHERE public_id = ?", String.class, id);
    }

    private List<String> memberships(JdbcTemplate db, UUID id) {
        return db.queryForList(
                "SELECT row_to_json(m)::text FROM project_membership m WHERE project_id = ? ORDER BY user_id",
                String.class,
                id);
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

    private void user(JdbcTemplate db, UUID id, UUID department, boolean manager) {
        db.update(
                "INSERT INTO app_user(id,email,full_name,status,created_at,department_id)"
                        + " SELECT ?,?,'PRC fixture','ACTIVE',now(),department_id FROM department WHERE public_id=?",
                id,
                id + "@prc-fixture.test",
                department);
        if (manager) {
            db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,'PROJECT_MANAGER',now())", id);
        }
    }
}
