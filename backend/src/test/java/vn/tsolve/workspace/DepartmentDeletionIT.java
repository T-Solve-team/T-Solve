package vn.tsolve.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
import vn.tsolve.identity.service.CompanyDomainFallback;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.service.DepartmentService;

/** UC-14 Delete Department on real PostgreSQL (KAN-122, STORY-005). */
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
@Import({TwoTenantDatabases.class, DepartmentDeletionIT.DomainConfiguration.class})
@WithTSolveUser(roles = Role.ADMIN)
class DepartmentDeletionIT {
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

    private int deleteAudits(UUID id) {
        return count(db(), """
                SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                AND details ->> 'operation' = 'DELETE'
                """, id);
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
    void readsProjectCountOfEveryStatus_AC_005_1() {
        UUID id = seed(prefix + " Count");
        project(id, prefix + "-A", "ACTIVE");
        project(id, prefix + "-B", "REMOVED");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(get("/api/v1/departments/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(prefix + " Count"))
                    .andExpect(jsonPath("$.projectCount").value(2));
            return null;
        });
    }

    @Test
    void deletesDepartmentWithProjectsSpaceAndAudit_AC_005_2_AC_005_3_AC_005_4_AC_005_5_AC_005_6() {
        UUID id = seed(prefix + " Gone");
        UUID active = project(id, prefix + "-A", "ACTIVE");
        UUID removed = project(id, prefix + "-B", "REMOVED");
        db().update("""
                UPDATE department SET default_project_id = (SELECT project_id FROM project WHERE public_id = ?)
                WHERE public_id = ?
                """, active, id);
        db().update(
                        "INSERT INTO app_user(email, full_name, status, created_at) VALUES (?, 'Mgr', 'ACTIVE', now())",
                        prefix + "@x.test");
        db().update("""
                INSERT INTO space_manager(space_id, user_id, assigned_at)
                SELECT s.space_id, u.id, now() FROM space s JOIN department d USING (department_id), app_user u
                WHERE d.public_id = ? AND u.email = ?
                """, id, prefix + "@x.test");
        TSolveUser actor = TSolveUser.current().orElseThrow();

        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id)).andExpect(status().isNoContent());
            return null;
        });

        var row = db().queryForMap("SELECT name, status, default_project_id FROM department WHERE public_id = ?", id);
        assertThat(row)
                .containsEntry("name", prefix + " Gone")
                .containsEntry("status", "DELETED")
                .containsEntry("default_project_id", null);
        assertThat(projectStatus(active)).isEqualTo("REMOVED");
        assertThat(projectStatus(removed)).isEqualTo("REMOVED");
        var space = db().queryForMap(
                        "SELECT s.space_id, s.public_id, s.name, s.status FROM space s JOIN department d USING (department_id)"
                                + " WHERE d.public_id = ?",
                        id);
        assertThat(space).containsEntry("status", "HIDDEN").containsEntry("name", prefix + " Gone");
        assertThat(count(db(), "SELECT count(*) FROM space_manager WHERE space_id = ?", space.get("space_id")))
                .isEqualTo(1);
        var audit = db().queryForMap("""
                SELECT action, target_type, actor_id, actor_email, details ->> 'operation' AS operation,
                       details ->> 'name' AS name, details ->> 'spaceId' AS space_id,
                       details -> 'removedProjectIds' AS removed
                FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                """, id);
        assertThat(audit)
                .containsEntry("action", "DEPARTMENT_CHANGE")
                .containsEntry("target_type", "DEPARTMENT")
                .containsEntry("actor_id", actor.id())
                .containsEntry("actor_email", actor.email())
                .containsEntry("operation", "DELETE")
                .containsEntry("name", prefix + " Gone")
                .containsEntry("space_id", space.get("public_id").toString());
        assertThat(audit.get("removed").toString()).contains(active.toString()).doesNotContain(removed.toString());
    }

    @Test
    void erasesNoRowAndKeepsUsersDepartment_AC_005_7() {
        UUID id = seed(prefix + " Keep");
        project(id, prefix + "-A", "ACTIVE");
        db().update("""
                INSERT INTO app_user(email, full_name, status, created_at, department_id)
                SELECT ?, 'Member', 'ACTIVE', now(), department_id FROM department WHERE public_id = ?
                """, prefix + "@x.test", id);
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id)).andExpect(status().isNoContent());
            return null;
        });
        assertThat(count(db(), "SELECT count(*) FROM department WHERE public_id = ?", id))
                .isEqualTo(1);
        assertThat(count(db(), "SELECT count(*) FROM project WHERE jira_project_key = ?", prefix + "-A"))
                .isEqualTo(1);
        assertThat(count(db(), """
                        SELECT count(*) FROM space s JOIN department d USING (department_id) WHERE d.public_id = ?
                        """, id)).isEqualTo(1);
        assertThat(count(db(), """
                        SELECT count(*) FROM app_user u JOIN department d USING (department_id)
                        WHERE u.email = ? AND u.status = 'ACTIVE' AND d.public_id = ?
                        """, prefix + "@x.test", id)).isEqualTo(1);
    }

    @Test
    void hidesDeletedDepartmentAndSpaceFromLists_AC_005_8() {
        UUID id = seed(prefix + " Hidden");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id)).andExpect(status().isNoContent());
            assertThat(field(json(get("/api/v1/users/departments")), "departmentId"))
                    .doesNotContain(id.toString());
            assertThat(field(json(get("/api/v1/departments")), "departmentId")).doesNotContain(id.toString());
            assertThat(field(json(get("/api/v1/users/creation-options")).get("spaces"), "name"))
                    .doesNotContain(prefix + " Hidden");
            return null;
        });
    }

    @Test
    void keepsNameReservedWhileDeleted_AC_005_9() {
        UUID id = seed(prefix + " Reserved");
        UUID other = seed(prefix + " Other");
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id)).andExpect(status().isNoContent());
            mvc.perform(post("/api/v1/departments")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("name", prefix + " Reserved"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG43"));
            mvc.perform(put("/api/v1/departments/" + other)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("name", prefix + " reserved"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MSG43"));
            return null;
        });
    }

    @Test
    void answersNotFoundForUnknownOrDeletedDepartment_AC_005_10() {
        UUID deleted = seed(db(), prefix + " Already", "DELETED");
        in(TwoTenantDatabases.ACME, () -> {
            for (UUID id : List.of(UUID.randomUUID(), deleted)) {
                mvc.perform(remove(id))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("MSG04"));
            }
            return null;
        });
        assertThat(deleteAudits(deleted)).isZero();
        assertThat(spaceStatusOf(deleted)).isEqualTo("HIDDEN");
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
    void concurrentDeletesApplyOne_AC_005_11() throws Exception {
        UUID id = seed(prefix + " Race");
        var responses = race(List.of(remove(id), remove(id)));
        assertThat(responses.stream().map(MockHttpServletResponse::getStatus).toList())
                .containsExactlyInAnyOrder(204, 404);
        assertThat(statusOf(id)).isEqualTo("DELETED");
        assertThat(deleteAudits(id)).isEqualTo(1);
    }

    @Test
    void rollsBackWhenSpaceIsMissing_AC_005_12() {
        UUID id = seed(prefix + " NoSpace");
        UUID active = project(id, prefix + "-A", "ACTIVE");
        db().update(
                        "DELETE FROM space WHERE department_id = (SELECT department_id FROM department WHERE public_id = ?)",
                        id);
        in(TwoTenantDatabases.ACME, () -> {
            mvc.perform(remove(id))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("MSG06"));
            return null;
        });
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
        assertThat(projectStatus(active)).isEqualTo("ACTIVE");
        assertThat(deleteAudits(id)).isZero();
    }

    @Test
    void rollsBackWhenAuditFails_AC_005_12() {
        UUID id = seed(prefix + " Audit");
        UUID active = project(id, prefix + "-A", "ACTIVE");
        var context = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            assertThatThrownBy(() -> scope.runIn(TwoTenantDatabases.ACME, () -> {
                        service.delete(id);
                        return null;
                    }))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            SecurityContextHolder.setContext(context);
        }
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
        assertThat(projectStatus(active)).isEqualTo("ACTIVE");
        assertThat(spaceStatusOf(id)).isEqualTo("ACTIVE");
        assertThat(deleteAudits(id)).isZero();
    }

    @Test
    void answersMsg06WithoutBoundWorkspace_AC_005_15() throws Exception {
        UUID id = seed(prefix + " Unbound");
        mvc.perform(remove(id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
        assertThat(deleteAudits(id)).isZero();
    }

    @Test
    void listsActiveSortedAndDeletedOnRequest_AC_005_16() {
        UUID b = seed(prefix + " b-active");
        UUID a = seed(prefix + " A-active");
        UUID gone = seed(db(), prefix + " c-deleted", "DELETED");
        in(TwoTenantDatabases.ACME, () -> {
            JsonNode active = json(get("/api/v1/departments"));
            List<String> ids = field(active, "departmentId");
            assertThat(ids).doesNotContain(gone.toString());
            assertThat(ids.indexOf(a.toString())).isLessThan(ids.indexOf(b.toString()));
            JsonNode all = json(get("/api/v1/departments").param("includeDeleted", "true"));
            List<JsonNode> rows = new ArrayList<>();
            all.forEach(rows::add);
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.get("departmentId").asText()).isEqualTo(gone.toString());
                assertThat(row.get("name").asText()).isEqualTo(prefix + " c-deleted");
                assertThat(row.get("status").asText()).isEqualTo("DELETED");
            });
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.get("departmentId").asText()).isEqualTo(a.toString());
                assertThat(row.get("status").asText()).isEqualTo("ACTIVE");
            });
            return null;
        });
    }
}
