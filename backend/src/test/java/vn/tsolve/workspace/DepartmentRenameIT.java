package vn.tsolve.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.dto.ChatbotProjectCheck;
import vn.tsolve.tracker.service.TrackerProjectService;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;
import vn.tsolve.workspace.service.DepartmentService;

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
@WithTSolveUser(roles = Role.ADMIN)
@ExtendWith(OutputCaptureExtension.class)
class DepartmentRenameIT {
    private final String prefix = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DepartmentService service;

    @MockBean
    private TrackerProjectService tracker;

    @AfterEach
    void cleanup() {
        for (JdbcTemplate db :
                List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB(), TwoTenantDatabases.main())) {
            new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource())).executeWithoutResult(tx -> {
                db.execute("ALTER TABLE audit_log_entry DISABLE TRIGGER audit_log_entry_no_update_delete");
                db.update("""
                        DELETE FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                        AND target_id IN (SELECT public_id FROM department WHERE lower(name) LIKE lower(?))
                        """, prefix + "%");
                db.execute("ALTER TABLE audit_log_entry ENABLE TRIGGER audit_log_entry_no_update_delete");
                db.update(
                        "UPDATE department SET default_project_id = NULL WHERE lower(name) LIKE lower(?)",
                        prefix + "%");
                db.update("DELETE FROM project WHERE jira_project_key LIKE ?", prefix + "%");
                db.update("""
                        DELETE FROM space_manager WHERE space_id IN (SELECT space_id FROM space
                        WHERE department_id IN (SELECT department_id FROM department WHERE lower(name) LIKE lower(?)))
                        """, prefix + "%");
                db.update("DELETE FROM app_user WHERE email LIKE ?", prefix + "%");
                db.update("""
                        DELETE FROM space
                        WHERE department_id IN (SELECT department_id FROM department WHERE lower(name) LIKE lower(?))
                        """, prefix + "%");
                db.update("DELETE FROM department WHERE lower(name) LIKE lower(?)", prefix + "%");
            });
        }
    }

    private int count(JdbcTemplate db, String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    private interface Call<T> {
        T run() throws Exception;
    }

    private <T> T in(UUID workspace, Call<T> call) {
        return scope.runIn(workspace, () -> {
            try {
                return call.run();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    /** Seeds an Active (or other status) Department with its Space directly, as the create endpoint would. */
    private UUID seed(JdbcTemplate db, String name, String status) {
        UUID id = db.queryForObject(
                "INSERT INTO department(name, status) VALUES (?, ?) RETURNING public_id", UUID.class, name, status);
        if ("ACTIVE".equals(status)) {
            db.update(
                    "INSERT INTO space(department_id, name) SELECT department_id, name FROM department WHERE public_id = ?",
                    id);
        }
        return id;
    }

    private UUID seed(String name) {
        return seed(TwoTenantDatabases.tenantA(), name, "ACTIVE");
    }

    private MockHttpServletRequestBuilder rename(UUID id, String name) throws Exception {
        return put("/api/v1/departments/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", name)));
    }

    private int audits(JdbcTemplate db, UUID id) {
        return count(db, "SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?", id);
    }

    private int updateAudits(JdbcTemplate db, UUID id) {
        return count(db, """
                SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                AND details ->> 'operation' = 'UPDATE'
                """, id);
    }

    private String nameOf(JdbcTemplate db, UUID id) {
        return db.queryForObject("SELECT name FROM department WHERE public_id = ?", String.class, id);
    }

    private String spaceNameOf(JdbcTemplate db, UUID id) {
        return db.queryForObject(
                "SELECT s.name FROM space s JOIN department d USING (department_id) WHERE d.public_id = ?",
                String.class,
                id);
    }

    @Test
    void readsActiveDepartment_AC_003_1() {
        UUID id = seed(prefix + " Read");
        in(TwoTenantDatabases.ACME, () -> {
            JsonNode body = mapper.readTree(mvc.perform(get("/api/v1/departments/" + id))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            assertThat(body.get("departmentId").asText()).isEqualTo(id.toString());
            assertThat(body.get("name").asText()).isEqualTo(prefix + " Read");
            return null;
        });
    }

    @Test
    void answersNotFoundForUnknownAndDeletedDepartment_AC_003_2() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID deleted = seed(db, prefix + " Gone", "DELETED");
        in(TwoTenantDatabases.ACME, () -> {
            for (UUID id : List.of(UUID.randomUUID(), deleted)) {
                mvc.perform(get("/api/v1/departments/" + id))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isNotFound())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                                .value("MSG04"));
                mvc.perform(rename(id, prefix + " Other"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isNotFound())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                                .value("MSG04"));
            }
            return null;
        });
        assertThat(nameOf(db, deleted)).isEqualTo(prefix + " Gone");
        assertThat(count(db, "SELECT count(*) FROM space WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isZero();
        assertThat(audits(db, deleted)).isZero();
    }

    @Test
    void renamesDepartmentWithSpaceAndAudit_AC_003_3_AC_003_4_AC_003_5() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Old");
        db.update(
                "UPDATE space SET status = 'HIDDEN' WHERE department_id = (SELECT department_id FROM department WHERE public_id = ?)",
                id);
        db.update(
                "INSERT INTO app_user(email, full_name, status, created_at) VALUES (?, 'Mgr', 'ACTIVE', now())",
                prefix + "@x.test");
        db.update("""
                INSERT INTO space_manager(space_id, user_id, assigned_at)
                SELECT s.space_id, u.id, now() FROM space s JOIN department d USING (department_id), app_user u
                WHERE d.public_id = ? AND u.email = ?
                """, id, prefix + "@x.test");
        var spaceBefore = db.queryForMap(
                "SELECT s.space_id, s.public_id FROM space s JOIN department d USING (department_id) WHERE d.public_id = ?",
                id);
        String stored = prefix + " New  Name";
        TSolveUser actor = TSolveUser.current().orElseThrow();
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, "  " + stored + " "))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.departmentId")
                                    .value(id.toString()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name")
                            .value(stored));
            return null;
        });
        assertThat(nameOf(db, id)).isEqualTo(stored);
        assertThat(spaceNameOf(db, id)).isEqualTo(stored);
        var space = db.queryForMap(
                "SELECT s.space_id, s.public_id, s.status FROM space s JOIN department d USING (department_id) WHERE d.public_id = ?",
                id);
        assertThat(space).containsEntry("space_id", spaceBefore.get("space_id")).containsEntry("status", "HIDDEN");
        assertThat(count(db, "SELECT count(*) FROM space_manager WHERE space_id = ?", space.get("space_id")))
                .isEqualTo(1);
        assertThat(audits(db, id)).isEqualTo(1);
        var audit = db.queryForMap("""
                SELECT action, target_type, actor_id, actor_email, details ->> 'operation' AS operation,
                       details -> 'before' ->> 'name' AS before_name, details -> 'after' ->> 'name' AS after_name,
                       details ->> 'spaceId' AS space_id
                FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                """, id);
        assertThat(audit)
                .containsEntry("action", "DEPARTMENT_CHANGE")
                .containsEntry("target_type", "DEPARTMENT")
                .containsEntry("actor_id", actor.id())
                .containsEntry("actor_email", actor.email())
                .containsEntry("operation", "UPDATE")
                .containsEntry("before_name", prefix + " Old")
                .containsEntry("after_name", stored)
                .containsEntry("space_id", space.get("public_id").toString());
    }

    @Test
    void keepsStatusDefaultProjectAndProjectsWhenTheDefaultIsSent_AC_003_6() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Keep");
        db.update("""
                INSERT INTO project(department_id, jira_project_key)
                SELECT department_id, ? FROM department WHERE public_id = ?
                """, prefix + "-A", id);
        db.update("""
                UPDATE department SET default_project_id = (SELECT project_id FROM project WHERE jira_project_key = ?)
                WHERE public_id = ?
                """, prefix + "-A", id);
        UUID projectId = db.queryForObject(
                "SELECT public_id FROM project WHERE jira_project_key = ?", UUID.class, prefix + "-A");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(save(id, prefix + " Kept", projectId))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk());
            return null;
        });
        verifyNoInteractions(tracker);
        var row = db.queryForMap("SELECT status, default_project_id, name FROM department WHERE public_id = ?", id);
        assertThat(row).containsEntry("status", "ACTIVE").containsEntry("name", prefix + " Kept");
        assertThat(row.get("default_project_id"))
                .isEqualTo(db.queryForObject(
                        "SELECT project_id FROM project WHERE jira_project_key = ?", Long.class, prefix + "-A"));
        assertThat(count(db, """
                        SELECT count(*) FROM project p JOIN department d USING (department_id)
                        WHERE d.public_id = ? AND p.jira_project_key = ?
                        """, id, prefix + "-A")).isEqualTo(1);
    }

    @Test
    void newNameIsReadBackAndListed_AC_003_7() {
        UUID id = seed(prefix + " Before");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, prefix + " After"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk());
            mvc.perform(get("/api/v1/departments/" + id))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name")
                            .value(prefix + " After"));
            JsonNode list = mapper.readTree(mvc.perform(get("/api/v1/users/departments"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            List<JsonNode> rows = new ArrayList<>();
            list.forEach(rows::add);
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.get("departmentId").asText()).isEqualTo(id.toString());
                assertThat(row.get("name").asText()).isEqualTo(prefix + " After");
            });
            return null;
        });
    }

    @Test
    void refusesNameOfAnotherActiveDepartment_AC_003_10() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Mine");
        seed(prefix + " Taken");
        in(TwoTenantDatabases.ACME, () -> {
            for (String name : List.of(prefix + " Taken", "  " + prefix + " Taken  ")) {
                mvc.perform(rename(id, name))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                                .value("MSG43"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.fieldErrors[0].field")
                                .value("name"));
            }
            return null;
        });
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Mine");
        assertThat(spaceNameOf(db, id)).isEqualTo(prefix + " Mine");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void refusesNameOfDeletedDepartment_AC_003_11() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Mine");
        seed(db, prefix + " Deleted", "DELETED");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, prefix + " Deleted"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                            .value("MSG43"));
            return null;
        });
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Mine");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void refusesCaseVariantOfAnotherName_AC_003_12() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Mine");
        seed(prefix + " Phòng IT");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, (prefix + " Phòng IT").toUpperCase(java.util.Locale.ROOT)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                            .value("MSG43"));
            return null;
        });
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Mine");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void allowsCaseOnlyChangeOfOwnName_AC_003_13() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " it support");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, prefix + " IT Support"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk());
            return null;
        });
        assertThat(nameOf(db, id)).isEqualTo(prefix + " IT Support");
        assertThat(spaceNameOf(db, id)).isEqualTo(prefix + " IT Support");
        assertThat(updateAudits(db, id)).isEqualTo(1);
    }

    @Test
    void sameNameChangesNothing_AC_003_14() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Same");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, "  " + prefix + " Same "))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name")
                            .value(prefix + " Same"));
            return null;
        });
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Same");
        assertThat(audits(db, id)).isZero();
    }

    private List<MockHttpServletResponse> race(List<MockHttpServletRequestBuilder> requests) throws Exception {
        var context = SecurityContextHolder.getContext();
        var ready = new CountDownLatch(requests.size());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(requests.size())) {
            var futures = new ArrayList<Future<MockHttpServletResponse>>();
            for (var request : requests) {
                Callable<MockHttpServletResponse> task = () -> {
                    SecurityContextHolder.setContext(context);
                    try {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("start timed out");
                        }
                        return in(
                                TwoTenantDatabases.ACME,
                                () -> mvc.perform(request).andReturn().getResponse());
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var responses = new ArrayList<MockHttpServletResponse>();
            for (var future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        }
    }

    @Test
    void concurrentRenamesToOneNameApplyOne_AC_003_15() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID a = seed(prefix + " A");
        UUID b = seed(prefix + " B");
        String target = prefix + " Target";
        var responses = race(List.of(rename(a, target), rename(b, target)));
        assertThat(responses.stream().map(MockHttpServletResponse::getStatus).toList())
                .containsExactlyInAnyOrder(200, 409);
        for (var response : responses) {
            if (response.getStatus() == 409) {
                assertThat(mapper.readTree(response.getContentAsString())
                                .get("code")
                                .asText())
                        .isEqualTo("MSG43");
            }
        }
        assertThat(count(db, "SELECT count(*) FROM department WHERE name = ?", target))
                .isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM space WHERE name = ?", target))
                .isEqualTo(1);
        assertThat(updateAudits(db, a) + updateAudits(db, b)).isEqualTo(1);
    }

    @Test
    void concurrentRenamesOfOneDepartmentRunInTurn_AC_003_16() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Start");
        var responses = race(List.of(rename(id, prefix + " One"), rename(id, prefix + " Two")));
        assertThat(responses.stream().map(MockHttpServletResponse::getStatus)).containsExactly(200, 200);
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT details -> 'before' ->> 'name' AS before_name, details -> 'after' ->> 'name' AS after_name
                FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                ORDER BY created_at
                """, id);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("before_name", prefix + " Start");
        assertThat(rows.get(1)).containsEntry("before_name", rows.get(0).get("after_name"));
        assertThat(nameOf(db, id)).isEqualTo(rows.get(1).get("after_name"));
        assertThat(spaceNameOf(db, id)).isEqualTo(nameOf(db, id));
    }

    @Test
    void rollsBackWhenSpaceIsMissing_AC_003_17() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " NoSpace");
        db.update(
                "DELETE FROM space WHERE department_id = (SELECT department_id FROM department WHERE public_id = ?)",
                id);
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, prefix + " Renamed"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isInternalServerError())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                            .value("MSG06"));
            return null;
        });
        assertThat(nameOf(db, id)).isEqualTo(prefix + " NoSpace");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void rollsBackWhenAuditFails_AC_003_17() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Audit");
        var context = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            assertThatThrownBy(() -> scope.runIn(
                            TwoTenantDatabases.ACME,
                            () -> service.update(id, new UpdateDepartmentRequest(prefix + " Renamed", null))))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            SecurityContextHolder.setContext(context);
        }
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Audit");
        assertThat(spaceNameOf(db, id)).isEqualTo(prefix + " Audit");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void allowsNameHeldInOtherWorkspaceAndWritesOnlyHere_AC_003_20() {
        JdbcTemplate a = TwoTenantDatabases.tenantA();
        JdbcTemplate b = TwoTenantDatabases.tenantB();
        String shared = prefix + " Shared";
        UUID id = seed(prefix + " Mine");
        seed(b, shared, "ACTIVE");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, shared))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk());
            return null;
        });
        assertThat(nameOf(a, id)).isEqualTo(shared);
        assertThat(count(a, "SELECT count(*) FROM department WHERE name = ?", shared))
                .isEqualTo(1);
        assertThat(count(b, "SELECT count(*) FROM department WHERE name = ?", shared))
                .isEqualTo(1);
        assertThat(count(b, "SELECT count(*) FROM department WHERE public_id = ?", id))
                .isZero();
        assertThat(count(TwoTenantDatabases.main(), "SELECT count(*) FROM department WHERE public_id = ?", id))
                .isZero();
        assertThat(audits(b, id)).isZero();
    }

    @Test
    void answersMsg06WithoutBoundWorkspace_AC_003_21() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Unbound");
        mvc.perform(get("/api/v1/departments/" + id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("MSG06"));
        mvc.perform(rename(id, prefix + " Renamed"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("MSG06"));
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Unbound");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void keepsEarlierAuditEntriesUnchanged_AC_003_22() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " First");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, prefix + " Second"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk());
            return null;
        });
        // A create entry written before the rename, as the create endpoint writes it.
        var created = Map.of("operation", "CREATE", "name", prefix + " First");
        db.update(
                "INSERT INTO audit_log_entry(actor_email, action, target_type, target_id, details, created_at)"
                        + " VALUES ('a@x.test', 'DEPARTMENT_CHANGE', 'DEPARTMENT', ?, ?::jsonb, now() - interval '1 hour')",
                id,
                toJson(created));
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(rename(id, prefix + " Third"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isOk());
            return null;
        });
        assertThat(count(db, """
                        SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                        AND details ->> 'operation' = 'CREATE' AND details ->> 'name' = ?
                        """, id, prefix + " First")).isEqualTo(1);
        assertThat(updateAudits(db, id)).isEqualTo(2);
    }

    // --- KAN-121: default Project (STORY-012) ---

    private UUID project(UUID department, String key, String status) {
        return TwoTenantDatabases.tenantA().queryForObject("""
                        INSERT INTO project(department_id, jira_project_key, status)
                        SELECT department_id, ?, ? FROM department WHERE public_id = ? RETURNING public_id
                        """, UUID.class, key, status, department);
    }

    private void setDefault(UUID department, UUID project) {
        TwoTenantDatabases.tenantA().update("""
                UPDATE department SET default_project_id = (SELECT project_id FROM project WHERE public_id = ?)
                WHERE public_id = ?
                """, project, department);
    }

    private UUID defaultOf(UUID department) {
        return TwoTenantDatabases.tenantA().queryForObject("""
                SELECT p.public_id FROM department d LEFT JOIN project p ON p.project_id = d.default_project_id
                WHERE d.public_id = ?
                """, UUID.class, department);
    }

    private MockHttpServletRequestBuilder save(UUID id, String name, UUID defaultProjectId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("defaultProjectId", defaultProjectId);
        return put("/api/v1/departments/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body));
    }

    private JsonNode perform(MockHttpServletRequestBuilder request, int status) {
        return in(TwoTenantDatabases.ACME, () -> {
            MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
            assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
            return mapper.readTree(response.getContentAsString());
        });
    }

    private List<Map<String, Object>> auditRows(UUID id) {
        return TwoTenantDatabases.tenantA().queryForList("""
                SELECT details::text AS details,
                       details -> 'before' -> 'defaultProject' ->> 'projectId' AS before_project,
                       details -> 'after' -> 'defaultProject' ->> 'projectId' AS after_project,
                       jsonb_typeof(details -> 'after' -> 'defaultProject') AS after_type,
                       details ->> 'spaceId' AS space_id
                FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                """, id);
    }

    @Test
    void listsTheDepartmentsActiveProjectsByKey_AC_012_1() {
        UUID id = seed(prefix + " List");
        UUID other = seed(prefix + " Other");
        UUID b = project(id, prefix + "-B", "ACTIVE");
        UUID a = project(id, prefix + "-A", "ACTIVE");
        project(id, prefix + "-C", "REMOVED");
        project(other, prefix + "-D", "ACTIVE");
        setDefault(id, b);

        JsonNode body = perform(get("/api/v1/departments/" + id), 200);

        assertThat(body.get("defaultProject").get("projectId").asText()).isEqualTo(b.toString());
        assertThat(body.get("defaultProject").get("jiraProjectKey").asText()).isEqualTo(prefix + "-B");
        List<String> keys = new ArrayList<>();
        body.get("projects")
                .forEach(option -> keys.add(option.get("jiraProjectKey").asText()));
        assertThat(keys).containsExactly(prefix + "-A", prefix + "-B");
        assertThat(body.get("projects").get(0).get("projectId").asText()).isEqualTo(a.toString());
    }

    @Test
    void readsARemovedDefaultAsNone_AC_012_3() {
        UUID id = seed(prefix + " Removed");
        UUID removed = project(id, prefix + "-R", "REMOVED");
        setDefault(id, removed);

        JsonNode body = perform(get("/api/v1/departments/" + id), 200);
        assertThat(body.get("defaultProject").isNull()).isTrue();
        assertThat(body.get("projects")).isEmpty();

        perform(save(id, prefix + " Removed", null), 200);
        assertThat(audits(TwoTenantDatabases.tenantA(), id)).isZero();
        verifyNoInteractions(tracker);
    }

    @Test
    void storesTheCheckedProjectAndAnswersTheDetail_AC_012_4_AC_012_2() {
        UUID id = seed(prefix + " Store");
        UUID ops = project(id, prefix + "-OPS", "ACTIVE");
        when(tracker.checkChatbotProject(prefix + "-OPS")).thenReturn(ChatbotProjectCheck.READY);

        JsonNode body = perform(save(id, prefix + " Store", ops), 200);

        assertThat(defaultOf(id)).isEqualTo(ops);
        assertThat(body.get("departmentId").asText()).isEqualTo(id.toString());
        assertThat(body.get("defaultProject").get("projectId").asText()).isEqualTo(ops.toString());
        assertThat(body.get("projects").get(0).get("jiraProjectKey").asText()).isEqualTo(prefix + "-OPS");
        verify(tracker).checkChatbotProject(prefix + "-OPS");
    }

    @Test
    void refusesAProjectThatIsNotAnActiveProjectOfThisDepartment_AC_012_5() {
        UUID id = seed(prefix + " Refuse");
        UUID other = seed(prefix + " Elsewhere");
        UUID foreign = project(other, prefix + "-F", "ACTIVE");
        UUID removed = project(id, prefix + "-X", "REMOVED");
        for (UUID projectId : List.of(foreign, removed, UUID.randomUUID())) {
            JsonNode body = perform(save(id, prefix + " Renamed", projectId), 400);
            assertThat(body.get("code").asText()).isEqualTo("MSG04");
            assertThat(body.get("fieldErrors").get(0).get("field").asText()).isEqualTo("defaultProjectId");
        }
        assertThat(defaultOf(id)).isNull();
        assertThat(nameOf(TwoTenantDatabases.tenantA(), id)).isEqualTo(prefix + " Refuse");
        assertThat(audits(TwoTenantDatabases.tenantA(), id)).isZero();
        verifyNoInteractions(tracker);
    }

    @Test
    void clearsTheDefaultWithNullOrAbsent_AC_012_6() throws Exception {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Clear");
        UUID ops = project(id, prefix + "-C1", "ACTIVE");
        setDefault(id, ops);

        perform(save(id, prefix + " Clear", null), 200);
        assertThat(defaultOf(id)).isNull();
        setDefault(id, ops);
        perform(rename(id, prefix + " Clear"), 200);
        assertThat(defaultOf(id)).isNull();

        List<Map<String, Object>> rows = auditRows(id);
        assertThat(rows).hasSize(2).allSatisfy(row -> {
            assertThat(row).containsEntry("before_project", ops.toString()).containsEntry("after_type", "null");
            assertThat(row.get("space_id")).isNull();
        });
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Clear");
        verifyNoInteractions(tracker);
    }

    @Test
    void changesOnlyTheDefaultWhenTheNameIsTheSame_AC_012_12() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Only");
        UUID ops = project(id, prefix + "-O", "ACTIVE");
        when(tracker.checkChatbotProject(prefix + "-O")).thenReturn(ChatbotProjectCheck.READY);

        perform(save(id, "  " + prefix + " Only ", ops), 200);

        assertThat(defaultOf(id)).isEqualTo(ops);
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Only");
        assertThat(spaceNameOf(db, id)).isEqualTo(prefix + " Only");
        List<Map<String, Object>> rows = auditRows(id);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("after_project", ops.toString()).containsEntry("before_project", null);
            assertThat(row.get("space_id")).isNull();
        });
    }

    @Test
    void keepsTheOldNameAndDefaultWhenTheNameIsTakenAfterTheCheck_AC_012_15() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Mine");
        seed(prefix + " Taken");
        UUID ops = project(id, prefix + "-T", "ACTIVE");
        when(tracker.checkChatbotProject(prefix + "-T")).thenReturn(ChatbotProjectCheck.READY);

        JsonNode body = perform(save(id, prefix + " Taken", ops), 409);

        assertThat(body.get("code").asText()).isEqualTo("MSG43");
        assertThat(defaultOf(id)).isNull();
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Mine");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void keepsTheOldNameAndDefaultWhenTheAuditFails_AC_012_15() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        UUID id = seed(prefix + " Audit");
        UUID ops = project(id, prefix + "-AU", "ACTIVE");
        when(tracker.checkChatbotProject(prefix + "-AU")).thenReturn(ChatbotProjectCheck.READY);
        var context = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            assertThatThrownBy(() -> scope.runIn(
                            TwoTenantDatabases.ACME,
                            () -> service.update(id, new UpdateDepartmentRequest(prefix + " Renamed", ops))))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            SecurityContextHolder.setContext(context);
        }
        assertThat(defaultOf(id)).isNull();
        assertThat(nameOf(db, id)).isEqualTo(prefix + " Audit");
        assertThat(audits(db, id)).isZero();
    }

    @Test
    void appliesTwoConcurrentDefaultChangesInTurn_AC_012_16_BUG_314() throws Exception {
        UUID id = seed(prefix + " Race");
        UUID a = project(id, prefix + "-RA", "ACTIVE");
        UUID b = project(id, prefix + "-RB", "ACTIVE");
        when(tracker.checkChatbotProject(any())).thenReturn(ChatbotProjectCheck.READY);

        var responses = race(List.of(save(id, prefix + " Race", a), save(id, prefix + " Race", b)));

        assertThat(responses.stream().map(MockHttpServletResponse::getStatus)).containsExactly(200, 200);
        List<Map<String, Object>> rows = auditRows(id);
        assertThat(rows).hasSize(2);
        Map<String, Object> first = rows.get(0).get("before_project") == null ? rows.get(0) : rows.get(1);
        Map<String, Object> second = first == rows.get(0) ? rows.get(1) : rows.get(0);
        assertThat(first.get("before_project")).isNull();
        assertThat(second.get("before_project")).isEqualTo(first.get("after_project"));
        assertThat(defaultOf(id)).hasToString((String) second.get("after_project"));
    }

    @Test
    void addsNoAuditWhenNameAndDefaultAreUnchanged_AC_003_14() {
        UUID id = seed(prefix + " Same");
        UUID ops = project(id, prefix + "-S", "ACTIVE");
        setDefault(id, ops);

        JsonNode body = perform(save(id, " " + prefix + " Same ", ops), 200);

        assertThat(body.get("defaultProject").get("projectId").asText()).isEqualTo(ops.toString());
        assertThat(audits(TwoTenantDatabases.tenantA(), id)).isZero();
        verifyNoInteractions(tracker);
    }

    @Test
    void keepsTokensOutOfLogsAndTheAudit_AC_012_17(CapturedOutput output) throws Exception {
        UUID id = seed(prefix + " Secret");
        UUID ops = project(id, prefix + "-SE", "ACTIVE");
        when(tracker.checkChatbotProject(prefix + "-SE")).thenReturn(ChatbotProjectCheck.READY);

        perform(save(id, prefix + " Secret", ops), 200);

        String details = (String) auditRows(id).get(0).get("details");
        assertThat(mapper.readTree(details).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("operation", "before", "after");
        assertThat(details).doesNotContainIgnoringCase("token").doesNotContain("Bearer");
        assertThat(output.getAll()).doesNotContain("Bearer ");
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
