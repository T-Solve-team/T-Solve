package vn.tsolve.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
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
class DepartmentCreationIT {
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
                db.update(
                        """
                        DELETE FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                        AND target_id IN (SELECT public_id FROM department WHERE lower(name) LIKE lower(?))
                        """,
                        prefix + "%");
                db.execute("ALTER TABLE audit_log_entry ENABLE TRIGGER audit_log_entry_no_update_delete");
                db.update(
                        """
                        DELETE FROM space
                        WHERE department_id IN (SELECT department_id FROM department WHERE lower(name) LIKE lower(?))
                        """,
                        prefix + "%");
                db.update("DELETE FROM department WHERE lower(name) LIKE lower(?)", prefix + "%");
            });
        }
    }

    private int count(JdbcTemplate db, String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    private MockHttpServletRequestBuilder create(String name) throws Exception {
        return post("/api/v1/departments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", name)));
    }

    @Test
    void createsActiveDepartmentWithSpaceAndAudit_AC_001_1_AC_001_2_AC_001_3() {
        String name = prefix + " IT  Support";
        TSolveUser actor = TSolveUser.current().orElseThrow();
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                JsonNode body = mapper.readTree(mvc.perform(create("  " + name + " "))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value(name))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
                UUID departmentId = UUID.fromString(body.get("departmentId").asText());
                JdbcTemplate db = TwoTenantDatabases.tenantA();
                assertThat(count(db, "SELECT count(*) FROM department WHERE name = ?", name))
                        .isEqualTo(1);
                var department = db.queryForMap(
                        "SELECT department_id, public_id, name, status FROM department WHERE name = ?", name);
                assertThat(department)
                        .containsEntry("public_id", departmentId)
                        .containsEntry("name", name)
                        .containsEntry("status", "ACTIVE");
                assertThat(count(
                                db,
                                "SELECT count(*) FROM space WHERE department_id = ?",
                                department.get("department_id")))
                        .isEqualTo(1);
                var space = db.queryForMap(
                        "SELECT space_id, public_id, name, status FROM space WHERE department_id = ?",
                        department.get("department_id"));
                assertThat(space).containsEntry("name", name).containsEntry("status", "ACTIVE");
                assertThat(count(db, "SELECT count(*) FROM space_manager WHERE space_id = ?", space.get("space_id")))
                        .isZero();
                assertThat(count(
                                db,
                                "SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id ="
                                        + " ?",
                                departmentId))
                        .isEqualTo(1);
                var audit = db.queryForMap(
                        """
                        SELECT action, target_type, target_id, actor_id, actor_email,
                               details ->> 'operation' AS operation, details ->> 'name' AS name,
                               details ->> 'spaceId' AS space_id
                        FROM audit_log_entry WHERE target_type = 'DEPARTMENT' AND target_id = ?
                        """,
                        departmentId);
                assertThat(audit)
                        .containsEntry("action", "DEPARTMENT_CHANGE")
                        .containsEntry("target_type", "DEPARTMENT")
                        .containsEntry("target_id", departmentId)
                        .containsEntry("actor_id", actor.id())
                        .containsEntry("actor_email", actor.email())
                        .containsEntry("operation", "CREATE")
                        .containsEntry("name", name)
                        .containsEntry("space_id", space.get("public_id").toString());
                return null;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void appearsInDepartmentList_AC_001_4() {
        String name = prefix + " Listed";
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                JsonNode created = mapper.readTree(mvc.perform(create(name))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
                UUID id = TwoTenantDatabases.tenantA()
                        .queryForObject("SELECT public_id FROM department WHERE name = ?", UUID.class, name);
                assertThat(created.get("departmentId").asText()).isEqualTo(id.toString());
                JsonNode body = mapper.readTree(mvc.perform(get("/api/v1/users/departments"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
                List<JsonNode> rows = new ArrayList<>();
                body.forEach(rows::add);
                assertThat(rows).anySatisfy(row -> {
                    assertThat(row.get("departmentId").asText()).isEqualTo(id.toString());
                    assertThat(row.get("name").asText()).isEqualTo(name);
                });
                return null;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void refusesDuplicateOfActiveName_AC_001_7() {
        String name = prefix + " Duplicate";
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                mvc.perform(create(name)).andExpect(status().isCreated());
                for (String duplicate : List.of(name, "  " + name + "  ")) {
                    mvc.perform(create(duplicate))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.code").value("MSG43"))
                            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
                }
                return null;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        assertThat(count(db, "SELECT count(*) FROM department WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM space WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isEqualTo(1);
        assertThat(count(
                        db,
                        """
                        SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                        AND lower(details ->> 'name') LIKE lower(?)
                        """,
                        prefix + "%"))
                .isEqualTo(1);
    }

    @Test
    void refusesNameOfDeletedDepartment_AC_001_8() {
        String name = prefix + " Deleted";
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        db.update("INSERT INTO department(name, status) VALUES (?, 'DELETED')", name);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                mvc.perform(create(name))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("MSG43"));
                return null;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        assertThat(count(db, "SELECT count(*) FROM department WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM department WHERE name = ?", String.class, name))
                .isEqualTo("DELETED");
        assertThat(count(db, "SELECT count(*) FROM space WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isZero();
        assertThat(count(
                        db,
                        """
                        SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                        AND lower(details ->> 'name') LIKE lower(?)
                        """,
                        prefix + "%"))
                .isZero();
    }

    @Test
    void refusesCaseVariantNames_AC_001_9() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                for (String name : List.of(prefix + "abc", prefix + " Phòng IT")) {
                    mvc.perform(create(name)).andExpect(status().isCreated());
                    mvc.perform(create(name.toUpperCase(Locale.ROOT)))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.code").value("MSG43"));
                    assertThat(count(db, "SELECT count(*) FROM department WHERE lower(name) = lower(?)", name))
                            .isEqualTo(1);
                    assertThat(count(db, "SELECT count(*) FROM space WHERE lower(name) = lower(?)", name))
                            .isEqualTo(1);
                    assertThat(count(
                                    db,
                                    """
                                    SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                                    AND lower(details ->> 'name') = lower(?)
                                    """,
                                    name))
                            .isEqualTo(1);
                }
                return null;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void concurrentRequestsCreateOneDepartment_AC_001_10() throws Exception {
        String name = prefix + " Concurrent";
        var context = SecurityContextHolder.getContext();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<MockHttpServletResponse>>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    SecurityContextHolder.setContext(context);
                    try {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("start timed out");
                        }
                        return scope.runIn(TwoTenantDatabases.ACME, () -> {
                            try {
                                return mvc.perform(create(name)).andReturn().getResponse();
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        });
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var future : futures) {
                var response = future.get(30, TimeUnit.SECONDS);
                statuses.add(response.getStatus());
                if (response.getStatus() == 409) {
                    assertThat(mapper.readTree(response.getContentAsString())
                                    .get("code")
                                    .asText())
                            .isEqualTo("MSG43");
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        assertThat(count(db, "SELECT count(*) FROM department WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isEqualTo(1);
        assertThat(count(db, "SELECT count(*) FROM space WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isEqualTo(1);
        assertThat(count(
                        db,
                        """
                        SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                        AND lower(details ->> 'name') LIKE lower(?)
                        """,
                        prefix + "%"))
                .isEqualTo(1);
    }

    @Test
    void rollsBackWhenAuditFails_AC_001_11() {
        String name = prefix + " Rollback";
        var context = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            assertThatThrownBy(() -> scope.runIn(
                            TwoTenantDatabases.ACME, () -> service.create(new CreateDepartmentRequest(name))))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            SecurityContextHolder.setContext(context);
        }
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        assertThat(count(db, "SELECT count(*) FROM department WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isZero();
        assertThat(count(db, "SELECT count(*) FROM space WHERE lower(name) LIKE lower(?)", prefix + "%"))
                .isZero();
        assertThat(count(
                        db,
                        """
                        SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                        AND lower(details ->> 'name') LIKE lower(?)
                        """,
                        prefix + "%"))
                .isZero();
    }

    @Test
    void allowsNameHeldInOtherWorkspace_AC_001_14() {
        String name = prefix + " Shared";
        TwoTenantDatabases.tenantB().update("INSERT INTO department(name) VALUES (?)", name);
        scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                JsonNode body = mapper.readTree(mvc.perform(create(name))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
                UUID id = UUID.fromString(body.get("departmentId").asText());
                assertThat(count(TwoTenantDatabases.tenantA(), "SELECT count(*) FROM department WHERE name = ?", name))
                        .isEqualTo(1);
                assertThat(count(TwoTenantDatabases.tenantB(), "SELECT count(*) FROM department WHERE name = ?", name))
                        .isEqualTo(1);
                assertThat(count(
                                TwoTenantDatabases.tenantA(),
                                "SELECT count(*) FROM department WHERE public_id = ?",
                                id))
                        .isEqualTo(1);
                assertThat(count(
                                TwoTenantDatabases.tenantB(),
                                "SELECT count(*) FROM department WHERE public_id = ?",
                                id))
                        .isZero();
                assertThat(count(TwoTenantDatabases.main(), "SELECT count(*) FROM department WHERE public_id = ?", id))
                        .isZero();
                return null;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void answersMsg06WithoutBoundWorkspace_AC_001_15() throws Exception {
        String name = prefix + " Unbound";
        mvc.perform(create(name))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MSG06"));
        for (JdbcTemplate db :
                List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB(), TwoTenantDatabases.main())) {
            assertThat(count(db, "SELECT count(*) FROM department WHERE lower(name) LIKE lower(?)", prefix + "%"))
                    .isZero();
            assertThat(count(db, "SELECT count(*) FROM space WHERE lower(name) LIKE lower(?)", prefix + "%"))
                    .isZero();
            assertThat(count(
                            db,
                            """
                            SELECT count(*) FROM audit_log_entry WHERE target_type = 'DEPARTMENT'
                            AND lower(details ->> 'name') LIKE lower(?)
                            """,
                            prefix + "%"))
                    .isZero();
        }
    }
}
