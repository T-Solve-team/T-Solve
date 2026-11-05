package vn.tsolve.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.tsolve.identity.UserListTestDatabase.A;
import static vn.tsolve.identity.UserListTestDatabase.ADMIN;
import static vn.tsolve.identity.UserListTestDatabase.B;
import static vn.tsolve.identity.UserListTestDatabase.DEPARTMENT;
import static vn.tsolve.identity.UserListTestDatabase.EMPLOYEE;
import static vn.tsolve.identity.UserListTestDatabase.id;
import static vn.tsolve.identity.UserListTestDatabase.owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.config.WebConfig;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.config.UserListSecurityConfig;
import vn.tsolve.identity.controller.UserListController;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.impl.UserListAccessServiceImpl;
import vn.tsolve.identity.service.impl.UserListServiceImpl;
import vn.tsolve.identity.service.impl.UserListTransaction;
import vn.tsolve.identity.session.WorkspaceSessionBinding;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = UserListIT.TestApplication.class)
@TestPropertySource(
        properties = {"tsolve.identity.user-list.enabled=true", "tsolve.cors.allowed-origins=http://localhost:5173"})
class UserListIT {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
        UserListSecurityConfig.class,
        UserListController.class,
        UserListRepository.class,
        UserListAccessServiceImpl.class,
        UserListServiceImpl.class,
        UserListTransaction.class,
        GlobalExceptionHandler.class,
        MethodSecurityConfig.class,
        WebConfig.class
    })
    static class TestApplication {
        @Bean
        UserListTestDatabase.TestTenantContext tenantContext() {
            return new UserListTestDatabase.TestTenantContext();
        }

        @Bean
        DataSource dataSource(UserListTestDatabase.TestTenantContext context) {
            return UserListTestDatabase.routing(context);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        UserListTestDatabase.TestTransactionManager transactionManager(
                DataSource dataSource, UserListTestDatabase.TestTenantContext context) {
            return new UserListTestDatabase.TestTransactionManager(dataSource, context);
        }

        @Bean
        ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json()
                    .build()
                    .addMixIn(
                            org.springframework.http.ProblemDetail.class,
                            org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);
        }
    }

    @Autowired
    private WebApplicationContext application;

    @Autowired
    private UserListTestDatabase.TestTenantContext context;

    @Autowired
    private UserListTestDatabase.TestTransactionManager manager;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc http;

    @BeforeEach
    void seed() {
        UserListTestDatabase.seed();
        manager.omitSetting = false;
        manager.wrongSetting = null;
        var resolver = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    jakarta.servlet.FilterChain chain)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                UUID workspace = "two.localhost".equals(request.getHeader("Host")) ? B : A;
                context.runIn(workspace, () -> {
                    try {
                        chain.doFilter(request, response);
                    } catch (java.io.IOException | jakarta.servlet.ServletException failure) {
                        throw new IllegalStateException(failure);
                    }
                });
            }
        };
        http = MockMvcBuilders.webAppContextSetup(application)
                .addFilters(resolver)
                .apply(springSecurity())
                .build();
    }

    @Test
    void readsOverlappingUuidAccountsInSeparateDatabases_AC_004_9_AC_004_10_AC_004_12_AC_004_13_AC_004_18_BUG_114()
            throws Exception {
        http.perform(get("/api/v1/users")
                        .header("Host", "one.localhost")
                        .session(session(A, ADMIN))
                        .header("X-Workspace-Id", B.toString())
                        .param("workspaceId", B.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(ADMIN.toString())))
                .andExpect(jsonPath("$.content[0].lastSignInAt").doesNotExist());
        http.perform(get("/api/v1/users").header("Host", "two.localhost").session(session(B, ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath(
                        "$.content[*].fullName", org.hamcrest.Matchers.containsInAnyOrder("b Admin", "b Employee")));
        assertClean();
    }

    @Test
    void filtersAndPagesAndReadsRetainedDepartments_AC_004_11_AC_004_13_AC_004_14_AC_004_15() throws Exception {
        var session = session(A, ADMIN);
        http.perform(get("/api/v1/users")
                        .session(session)
                        .param("q", "  RESOLVER ")
                        .param("role", "PROJECT_MANAGER")
                        .param("departmentId", DEPARTMENT.toString())
                        .param("status", "ACTIVE")
                        .param("size", "1")
                        .param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath(
                        "$.content[0].roles",
                        org.hamcrest.Matchers.containsInAnyOrder("EMPLOYEE", "TICKET_RESOLVER", "PROJECT_MANAGER")));
        http.perform(get("/api/v1/users").session(session).param("role", "EMPLOYEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
        http.perform(get("/api/v1/users/departments").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].name").value("Retained"))
                .andExpect(jsonPath("$[0].departmentId").value(DEPARTMENT.toString()));
        http.perform(get("/api/v1/users").session(session).param("page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(5));
        http.perform(get("/api/v1/users")
                        .session(session)
                        .param("departmentId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        assertClean();
    }

    @Test
    void returnsCodedValidationAndCanonicalSecurityErrors_AC_004_14_AC_004_16_BUG_113() throws Exception {
        http.perform(get("/api/v1/users").session(session(A, ADMIN)).param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"))
                .andExpect(jsonPath("$.fieldErrors[0].params.max_length").value(100));
        for (String[] pair : List.of(
                new String[] {"departmentId", "1-1-1-1-1"},
                new String[] {"sort", "lastSignInAt,desc"},
                new String[] {"size", "101"})) {
            http.perform(get("/api/v1/users").session(session(A, ADMIN)).param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MSG06"));
        }
        http.perform(get("/api/v1/users").queryParam("token", "not-in-instance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"))
                .andExpect(jsonPath("$.detail").value("Your session has expired. Please log in again."))
                .andExpect(jsonPath("$.params").isMap())
                .andExpect(jsonPath("$.instance").value("/api/v1/users"));
        assertClean();
    }

    @Test
    void rejectsForeignMissingAndStaleSessionRoles_AC_004_16_AC_004_18_BUG_115() throws Exception {
        for (String path : List.of("/api/v1/users", "/api/v1/users/departments")) {
            http.perform(get(path)).andExpect(status().isUnauthorized());
            http.perform(get(path).header("Host", "two.localhost").session(session(A, ADMIN)))
                    .andExpect(status().isUnauthorized());
            http.perform(get(path).session(session(A, EMPLOYEE))).andExpect(status().isForbidden());
            http.perform(get(path).session(session(A, id(99)))).andExpect(status().isUnauthorized());
        }
        var session = session(A, ADMIN);
        http.perform(get("/api/v1/users").session(session)).andExpect(status().isOk());
        owner(A).update("DELETE FROM user_role WHERE user_id=?", ADMIN);
        http.perform(get("/api/v1/users").session(session)).andExpect(status().isForbidden());
        owner(A).update("INSERT INTO user_role VALUES(?,'ADMIN')", ADMIN);
        http.perform(get("/api/v1/users").session(session)).andExpect(status().isOk());
        owner(A).update("UPDATE app_user SET status='DEACTIVATED' WHERE id=?", ADMIN);
        http.perform(get("/api/v1/users").session(session)).andExpect(status().isUnauthorized());
        assertClean();
    }

    @Test
    void deniesWrongOrMissingPlatformGuardWithoutLeakingState_AC_004_18_AC_004_19_BUG_114_BUG_121() throws Exception {
        manager.omitSetting = true;
        http.perform(get("/api/v1/users").session(session(A, ADMIN)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
        manager.omitSetting = false;
        manager.wrongSetting = B;
        http.perform(get("/api/v1/users/departments").session(session(A, ADMIN)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MSG06"));
        manager.wrongSetting = null;
        owner(A).update("UPDATE tenant_identity SET tenant_id=?", B);
        http.perform(get("/api/v1/users").session(session(A, ADMIN))).andExpect(status().isServiceUnavailable());
        owner(A).update("UPDATE tenant_identity SET tenant_id=?", A);
        http.perform(get("/api/v1/users").session(session(A, ADMIN))).andExpect(status().isOk());
        assertClean();
    }

    @Test
    void preservesAllowedCorsBeforeAuthenticatedReads_AC_004_16_BUG_112() throws Exception {
        http.perform(options("/api/v1/users")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        http.perform(get("/api/v1/users")
                        .header("Origin", "http://localhost:5173")
                        .session(session(A, ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        http.perform(get("/api/v1/users").header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        http.perform(get("/api/v1/users")
                        .header("Origin", "https://foreign.example")
                        .session(session(A, ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        assertClean();
    }

    @Test
    void readsDoNotWriteAccountData_AC_004_12_AC_004_19() throws Exception {
        var before = owner(A).queryForList("SELECT * FROM app_user ORDER BY id");
        var roles = owner(A).queryForList("SELECT * FROM user_role ORDER BY user_id,role");
        http.perform(get("/api/v1/users").session(session(A, ADMIN))).andExpect(status().isOk());
        assertThat(owner(A).queryForList("SELECT * FROM app_user ORDER BY id")).isEqualTo(before);
        assertThat(owner(A).queryForList("SELECT * FROM user_role ORDER BY user_id,role"))
                .isEqualTo(roles);
        assertClean();
    }

    private void assertClean() {
        assertThat(context.currentWorkspaceId()).isEmpty();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        assertThat(context.runIn(
                        A,
                        () -> jdbc.queryForObject(
                                "SELECT NULLIF(current_setting('app.tenant_id',true),'')", String.class)))
                .isNull();
        assertThat(context.runIn(A, () -> jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class)))
                .isZero();
    }

    private static MockHttpSession session(UUID workspace, UUID account) {
        var principal = new TSolveUser(account, "fixture@example.test", "Fixture", Set.of(Role.EMPLOYEE));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        authentication.setDetails(new WorkspaceSessionBinding(workspace));
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(authentication);
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return session;
    }
}
