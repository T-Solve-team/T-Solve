package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.service.WorkspaceEmailRegistry;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

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
@Import({TwoTenantDatabases.class, UserCreationIT.DomainConfiguration.class})
@WithTSolveUser(roles = Role.ADMIN)
class UserCreationIT {
    private static final String DOMAIN = "create-fixture.test";
    private final UUID department = UUID.randomUUID();
    private final UUID otherDepartment = UUID.randomUUID();
    private final UUID hiddenDepartment = UUID.randomUUID();
    private final UUID space = UUID.randomUUID();
    private final UUID otherSpace = UUID.randomUUID();
    private final UUID hiddenSpace = UUID.randomUUID();
    private final String prefix = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private WorkspaceEmailRegistry registry;

    /** Domain comes from the real lookup port; the production mock fallback is not enabled in this IT. */
    @TestConfiguration(proxyBeanMethods = false)
    static class DomainConfiguration {
        @Bean
        @Primary
        WorkspaceLookup creationWorkspaceLookup() {
            return new WorkspaceLookup() {
                @Override
                public Optional<WorkspaceRef> findById(UUID id) {
                    return Optional.of(new WorkspaceRef(
                            id, "fixture.localhost", DOMAIN, WorkspaceRef.Status.ACTIVE, "fixture", null));
                }

                @Override
                public Optional<WorkspaceRef> findBySiteHost(String host) {
                    return Optional.empty();
                }
            };
        }
    }

    @BeforeEach
    void seed() {
        when(registry.claim(any(), anyString())).thenReturn(true);
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        for (UUID id : List.of(department, otherDepartment, hiddenDepartment)) {
            db.update("INSERT INTO department(public_id, name, status) VALUES (?, ?, 'ACTIVE')", id, "Creation " + id);
        }
        addSpace(space, department, "Zeta " + prefix, "ACTIVE");
        addSpace(otherSpace, otherDepartment, "alpha " + prefix, "ACTIVE");
        addSpace(hiddenSpace, hiddenDepartment, "Hidden " + prefix, "HIDDEN");
    }

    private void addSpace(UUID id, UUID dept, String name, String status) {
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO space(public_id, department_id, name, status) SELECT ?, department_id, ?, ? FROM department WHERE public_id = ?",
                        id,
                        name,
                        status,
                        dept);
    }

    @AfterEach
    void cleanup() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            // Audit is append-only in production. Disable its delete trigger only inside this test transaction,
            // delete exact fixture targets, and restore the trigger before committing.
            new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource())).executeWithoutResult(tx -> {
                db.execute("ALTER TABLE audit_log_entry DISABLE TRIGGER audit_log_entry_no_update_delete");
                db.update(
                        "DELETE FROM audit_log_entry WHERE target_type = 'USER' AND target_id IN (SELECT id FROM app_user WHERE email LIKE ?)",
                        prefix + "%@" + DOMAIN);
                db.execute("ALTER TABLE audit_log_entry ENABLE TRIGGER audit_log_entry_no_update_delete");
                db.update(
                        "DELETE FROM space_manager WHERE space_id IN (SELECT space_id FROM space WHERE public_id IN (?, ?, ?))",
                        space,
                        otherSpace,
                        hiddenSpace);
                db.update("DELETE FROM space WHERE public_id IN (?, ?, ?)", space, otherSpace, hiddenSpace);
                db.update(
                        "DELETE FROM user_role WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE ?)",
                        prefix + "%@" + DOMAIN);
                db.update("DELETE FROM app_user WHERE email LIKE ?", prefix + "%@" + DOMAIN);
                db.update(
                        "DELETE FROM department WHERE public_id IN (?, ?, ?)",
                        department,
                        otherDepartment,
                        hiddenDepartment);
            });
        }
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(request);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private MockHttpServletRequestBuilder create(String suffix, List<String> roles, UUID dept, List<UUID> spaces)
            throws Exception {
        var input = new java.util.HashMap<String, Object>();
        input.put("email", " " + prefix + suffix + "@" + DOMAIN.toUpperCase(java.util.Locale.ROOT) + " ");
        input.put("roles", roles);
        input.put("departmentId", dept);
        input.put("spaceIds", spaces);
        return post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input));
    }

    private JsonNode created(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode body = mapper.readTree(perform(request)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return body;
    }

    @Test
    void createsOnlyInBoundTenantWithRolesDepartmentSpacesAndAudit_AC_005_9_AC_005_17() throws Exception {
        JsonNode body = created(create(
                "account",
                List.of("PROJECT_MANAGER", "KNOWLEDGE_MANAGER", "ADMIN", "ADMIN"),
                department,
                List.of(space, space)));
        UUID id = UUID.fromString(body.get("id").asText());
        assertThat(body.get("email").asText()).isEqualTo(prefix + "account@" + DOMAIN);
        assertThat(body.get("fullName").isNull()).isTrue();
        assertThat(body.get("lastSignInAt").isNull()).isTrue();
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.at("/department/departmentId").asText()).isEqualTo(department.toString());
        assertThat(body.get("roles").toString()).contains("EMPLOYEE", "PROJECT_MANAGER", "KNOWLEDGE_MANAGER", "ADMIN");
        JdbcTemplate db = TwoTenantDatabases.tenantA();
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM app_user WHERE id = ? AND full_name IS NULL AND first_sign_in_at IS NULL AND last_sign_in_at IS NULL AND created_at IS NOT NULL AND department_id IS NOT NULL",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, id))
                .isEqualTo(3);
        assertThat(db.queryForObject("SELECT count(*) FROM space_manager WHERE user_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(db.queryForObject(
                        "SELECT count(*) FROM audit_log_entry WHERE target_id = ? AND target_type = 'USER' AND action = 'ROLE_CHANGE'",
                        Integer.class,
                        id))
                .isEqualTo(1);
        JsonNode details = mapper.readTree(
                db.queryForObject("SELECT details::text FROM audit_log_entry WHERE target_id = ?", String.class, id));
        assertThat(details.get("departmentId").asText()).isEqualTo(department.toString());
        assertThat(details.get("spaceIds").get(0).asText()).isEqualTo(space.toString());
        assertThat(details.get("roles").toString()).contains("ADMIN", "EMPLOYEE");
        JdbcTemplate beta = TwoTenantDatabases.tenantB();
        assertThat(beta.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Integer.class, id))
                .isZero();
        assertThat(beta.queryForObject("SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, id))
                .isZero();
        assertThat(beta.queryForObject("SELECT count(*) FROM space_manager WHERE user_id = ?", Integer.class, id))
                .isZero();
        assertThat(beta.queryForObject("SELECT count(*) FROM audit_log_entry WHERE target_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void sameEmailInBetaDoesNotBlockAcme_AC_005_16() throws Exception {
        TwoTenantDatabases.tenantB()
                .update(
                        "INSERT INTO app_user(email, status, created_at) VALUES (?, 'ACTIVE', now())",
                        prefix + "isolation@" + DOMAIN);
        created(create("isolation", List.of(), null, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DEACTIVATED"})
    void rejectsExistingAccount_AC_005_11(String state) throws Exception {
        TwoTenantDatabases.tenantA()
                .update(
                        "INSERT INTO app_user(email, status, created_at) VALUES (?, ?, now())",
                        prefix + "duplicate@" + DOMAIN,
                        state);
        perform(create("duplicate", List.of(), null, List.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(state.equals("ACTIVE") ? "MSG11" : "MSG111"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void rejectsProjectManagerWithoutDepartment_AC_005_12() throws Exception {
        perform(create("pm", List.of("PROJECT_MANAGER"), null, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("departmentId"));
        assertNoAccount("pm");
    }

    @Test
    void rejectsDeactivatedDepartment_AC_005_12() throws Exception {
        TwoTenantDatabases.tenantA()
                .update("UPDATE department SET status = 'DEACTIVATED' WHERE public_id = ?", department);
        perform(create("inactive", List.of(), department, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("departmentId"));
        assertNoAccount("inactive");
    }

    @Test
    void rejectsKnowledgeManagerWithoutSpaceAndHiddenSpace_AC_005_13() throws Exception {
        for (List<UUID> spaces : List.of(List.<UUID>of(), List.of(hiddenSpace))) {
            perform(create("km", List.of("KNOWLEDGE_MANAGER"), null, spaces))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG01"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("spaceIds"));
        }
        assertNoAccount("km");
    }

    @Test
    void rejectsOccupiedSpace_AC_005_13() throws Exception {
        created(create("owner", List.of("KNOWLEDGE_MANAGER"), null, List.of(space)));
        perform(create("second", List.of("KNOWLEDGE_MANAGER"), null, List.of(space)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG117"))
                .andExpect(jsonPath("$.params.space_name").value("Zeta " + prefix));
        assertNoAccount("second");
    }

    @Test
    void concurrentClaimsLeaveOneManagerAndOneConflict_AC_005_13() throws Exception {
        var context = SecurityContextHolder.getContext();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures =
                    new ArrayList<java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse>>();
            for (String suffix : List.of("race1", "race2")) {
                futures.add(pool.submit(() -> {
                    SecurityContextHolder.setContext(context);
                    try {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("start timed out");
                        }
                        return perform(create(suffix, List.of("KNOWLEDGE_MANAGER"), null, List.of(space)))
                                .andReturn()
                                .getResponse();
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
                JsonNode body = mapper.readTree(response.getContentAsString());
                if (response.getStatus() != 201) {
                    assertThat(body.get("code").asText()).isEqualTo("MSG117");
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM space_manager WHERE space_id = (SELECT space_id FROM space WHERE public_id = ?)",
                                Integer.class,
                                space))
                .isEqualTo(1);
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM app_user WHERE email IN (?, ?)",
                                Integer.class,
                                prefix + "race1@" + DOMAIN,
                                prefix + "race2@" + DOMAIN))
                .isEqualTo(1);
    }

    @Test
    void registryRefusalCreatesNothing_AC_005_16() throws Exception {
        when(registry.claim(TwoTenantDatabases.ACME, prefix + "refused@" + DOMAIN))
                .thenReturn(false);
        perform(create("refused", List.of(), null, List.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MSG155"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        assertNoAccount("refused");
    }

    @Test
    void optionsIncludeOccupiedActiveSpacesInNameOrder_AC_005_14_AC_005_15() throws Exception {
        created(create("options", List.of("KNOWLEDGE_MANAGER"), null, List.of(space)));
        JsonNode body = mapper.readTree(perform(get("/api/v1/users/creation-options"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(body.get("companyDomain").asText()).isEqualTo(DOMAIN);
        List<String> ids = new ArrayList<>();
        for (JsonNode row : body.get("spaces")) {
            if (row.get("name").asText().endsWith(prefix)) {
                ids.add(row.get("spaceId").asText());
            }
        }
        assertThat(ids).containsExactly(otherSpace.toString(), space.toString());
    }

    private void assertNoAccount(String suffix) {
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM app_user WHERE email = ?",
                                Integer.class,
                                prefix + suffix + "@" + DOMAIN))
                .isZero();
    }
}
