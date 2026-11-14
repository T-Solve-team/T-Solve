package vn.tsolve.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import vn.tsolve.identity.service.CompanyDomainFallback;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.service.DepartmentService;

/** UC-15 Restore Department on real PostgreSQL (KAN-123, STORY-007). */
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
@Import({TwoTenantDatabases.class, DepartmentRestoreIT.DomainConfiguration.class})
@WithTSolveUser(roles = Role.ADMIN)
class DepartmentRestoreIT {
    /** creation-options needs a company domain; the identity-mock fallback is not enabled in this IT (AC-005.8). */
    @TestConfiguration(proxyBeanMethods = false)
    static class DomainConfiguration {
        @Bean
        CompanyDomainFallback companyDomainFallback() {
            return () -> "acme.test";
        }
    }

    private final String prefix = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DepartmentService service;

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

    private static JdbcTemplate db() {
        return TwoTenantDatabases.tenantA();
    }

    private int count(JdbcTemplate db, String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    /** A Department with its Space, as the create endpoint writes them. */
    private UUID seed(JdbcTemplate db, String name, String status) {
        UUID id = db.queryForObject(
                "INSERT INTO department(name, status) VALUES (?, ?) RETURNING public_id", UUID.class, name, status);
        db.update(
                "INSERT INTO space(department_id, name, status) SELECT department_id, name, ? FROM department"
                        + " WHERE public_id = ?",
                "ACTIVE".equals(status) ? "ACTIVE" : "HIDDEN",
                id);
        return id;
    }

    private UUID seed(String name) {
        return seed(db(), name, "ACTIVE");
    }

    private UUID project(UUID department, String key, String status) {
        return db().queryForObject("""
                INSERT INTO project(department_id, jira_project_key, status)
                SELECT department_id, ?, ? FROM department WHERE public_id = ? RETURNING public_id
                """, UUID.class, key, status, department);
    }

    private String statusOf(UUID id) {
        return db().queryForObject("SELECT status FROM department WHERE public_id = ?", String.class, id);
    }

    private String spaceStatusOf(UUID id) {
        return db().queryForObject(
                        "SELECT s.status FROM space s JOIN department d USING (department_id) WHERE d.public_id = ?",
                        String.class,
                        id);
    }

    private String projectStatus(UUID projectId) {
        return db().queryForObject("SELECT status FROM project WHERE public_id = ?", String.class, projectId);
    }

    private int audits(UUID id, String operation) {
        return count(db(), """
                SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                AND details ->> 'operation' = ?
                """, id, operation);
    }

    private static MockHttpServletRequestBuilder restore(UUID id) {
        return post("/api/v1/departments/" + id + "/restore").with(csrf());
    }

    private static MockHttpServletRequestBuilder remove(UUID id) {
        return delete("/api/v1/departments/" + id).with(csrf());
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString());
    }

    private List<String> field(JsonNode array, String name) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(name).asText()));
        return values;
    }

    @Test
    void restoresDepartmentAndSpaceWithAudit_AC_007_1_AC_007_2_AC_007_4() {
        UUID id = seed(db(), prefix + " Back", "DELETED");
        TSolveUser actor = TSolveUser.current().orElseThrow();
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(restore(id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.departmentId").value(id.toString()))
                    .andExpect(jsonPath("$.name").value(prefix + " Back"));
            return null;
        });
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
        assertThat(spaceStatusOf(id)).isEqualTo("ACTIVE");
        String spaceId = db().queryForObject(
                        "SELECT s.public_id::text FROM space s JOIN department d USING (department_id) WHERE d.public_id = ?",
                        String.class,
                        id);
        var audit = db().queryForMap("""
                SELECT action, target_type, actor_id, actor_email, details ->> 'operation' AS operation,
                       details ->> 'name' AS name, details ->> 'spaceId' AS space_id
                FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                """, id);
        assertThat(audit)
                .containsEntry("action", "DEPARTMENT_CHANGE")
                .containsEntry("target_type", "DEPARTMENT")
                .containsEntry("actor_id", actor.id())
                .containsEntry("actor_email", actor.email())
                .containsEntry("operation", "RESTORE")
                .containsEntry("name", prefix + " Back")
                .containsEntry("space_id", spaceId);
    }

    @Test
    void keepsProjectsRemovedAndDefaultProjectEmpty_AC_007_3() {
        UUID id = seed(prefix + " Projects");
        UUID active = project(id, prefix + "-A", "ACTIVE");
        db().update("""
                UPDATE department SET default_project_id = (SELECT project_id FROM project WHERE public_id = ?)
                WHERE public_id = ?
                """, active, id);
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id)).andExpect(status().isNoContent());
            mvc.perform(restore(id)).andExpect(status().isOk());
            return null;
        });
        assertThat(projectStatus(active)).isEqualTo("REMOVED");
        assertThat(db().queryForMap("SELECT default_project_id FROM department WHERE public_id = ?", id))
                .containsEntry("default_project_id", null);
    }

    @Test
    void listsAndReadsRestoredDepartmentAndSpaceAgain_AC_007_5() {
        UUID id = seed(prefix + " Again");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id)).andExpect(status().isNoContent());
            mvc.perform(restore(id)).andExpect(status().isOk());
            assertThat(field(json(get("/api/v1/users/departments")), "departmentId"))
                    .contains(id.toString());
            assertThat(field(json(get("/api/v1/departments")), "departmentId")).contains(id.toString());
            mvc.perform(get("/api/v1/departments/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(prefix + " Again"));
            assertThat(field(json(get("/api/v1/users/creation-options")).get("spaces"), "name"))
                    .contains(prefix + " Again");
            return null;
        });
    }

    @Test
    void answersNotFoundForUnknownOrActiveDepartment_AC_007_6() {
        UUID active = seed(prefix + " Active");
        in(TwoTenantDatabases.ACME, () -> {
            for (UUID id : List.of(UUID.randomUUID(), active)) {
                mvc.perform(restore(id))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("MSG04"));
            }
            return null;
        });
        assertThat(audits(active, "RESTORE")).isZero();
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
    void concurrentRestoresApplyOne_AC_007_7() throws Exception {
        UUID id = seed(db(), prefix + " Race", "DELETED");
        var responses = race(List.of(restore(id), restore(id)));
        assertThat(responses.stream().map(MockHttpServletResponse::getStatus).toList())
                .containsExactlyInAnyOrder(200, 404);
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
        assertThat(audits(id, "RESTORE")).isEqualTo(1);
    }

    @Test
    void rollsBackWhenSpaceIsMissing_AC_007_8() {
        UUID id = seed(db(), prefix + " NoSpace", "DELETED");
        db().update(
                        "DELETE FROM space WHERE department_id = (SELECT department_id FROM department WHERE public_id = ?)",
                        id);
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(restore(id))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("MSG06"));
            return null;
        });
        assertThat(statusOf(id)).isEqualTo("DELETED");
        assertThat(audits(id, "RESTORE")).isZero();
    }

    @Test
    void rollsBackWhenAuditFails_AC_007_8() {
        UUID id = seed(db(), prefix + " Audit", "DELETED");
        var context = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            assertThatThrownBy(() -> scope.runIn(TwoTenantDatabases.ACME, () -> service.restore(id)))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            SecurityContextHolder.setContext(context);
        }
        assertThat(statusOf(id)).isEqualTo("DELETED");
        assertThat(spaceStatusOf(id)).isEqualTo("HIDDEN");
        assertThat(audits(id, "RESTORE")).isZero();
    }

    @Test
    void answersMsg06WithoutBoundWorkspace_AC_007_11() throws Exception {
        UUID id = seed(db(), prefix + " Unbound", "DELETED");
        mvc.perform(restore(id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
        assertThat(statusOf(id)).isEqualTo("DELETED");
    }

    @Test
    void keepsEarlierAuditEntriesUnchanged_AC_007_12() {
        UUID id = seed(prefix + " History");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id)).andExpect(status().isNoContent());
            mvc.perform(restore(id)).andExpect(status().isOk());
            return null;
        });
        assertThat(audits(id, "DELETE")).isEqualTo(1);
        assertThat(audits(id, "RESTORE")).isEqualTo(1);
        assertThat(db().queryForObject("""
                        SELECT details ->> 'name' FROM audit_log_entry
                        WHERE target_type = 'DEPARTMENT' AND target_id = ? AND details ->> 'operation' = 'DELETE'
                        """, String.class, id)).isEqualTo(prefix + " History");
    }
}
