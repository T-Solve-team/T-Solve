package vn.tsolve.identity.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.controller.UserListController;
import vn.tsolve.identity.service.UserListService;
import vn.tsolve.identity.signin.port.LocalTenantScope;
import vn.tsolve.identity.signin.port.TenantScope;

@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UserListController.class))
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    MockIdentityConfiguration.class,
    MockSignedInCallerFilterTest.ScopeConfiguration.class
})
@ActiveProfiles("identity-mock")
class MockSignedInCallerFilterTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private MockIdentityProperties properties;

    @MockBean
    private MockCallerReader reader;

    @MockBean
    private UserListService service;

    @TestConfiguration(proxyBeanMethods = false)
    static class ScopeConfiguration {
        @Bean
        TenantScope scope() {
            return new LocalTenantScope();
        }
    }

    @BeforeEach
    void prepare() {
        when(service.list(any())).thenAnswer(invocation -> {
            assertThat(scope.currentWorkspaceId()).isEqualTo(properties.workspaceId());
            return Page.empty();
        });
    }

    @Test
    void defaultSecurityRepositoryLoadsMockContext_AC_004_17() throws Exception {
        when(reader.read(properties.accountEmail()))
                .thenReturn(Optional.of(
                        new TSolveUser(UUID.randomUUID(), properties.accountEmail(), "Admin", Set.of(Role.ADMIN))));
        mvc.perform(get("/api/v1/users")).andExpect(status().isOk());
        verify(reader).read(properties.accountEmail());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThatThrownBy(scope::currentWorkspaceId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingOrDeactivatedCallerStaysAnonymous_AC_004_17() throws Exception {
        when(reader.read(properties.accountEmail())).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
    }

    @Test
    void rereadsRolesForEveryRequest_AC_004_17() throws Exception {
        UUID id = UUID.randomUUID();
        when(reader.read(properties.accountEmail()))
                .thenReturn(
                        Optional.of(new TSolveUser(id, properties.accountEmail(), "Admin", Set.of(Role.ADMIN))),
                        Optional.of(new TSolveUser(id, properties.accountEmail(), "Admin", Set.of(Role.EMPLOYEE))));
        mvc.perform(get("/api/v1/users")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
    }

    @Test
    void skipsSignInPaths_KAN_297_Minor_6() {
        var filter = new MockSignedInCallerFilter(scope, reader, properties);
        var signIn = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/auth/sign-in/start");
        signIn.setRequestURI("/api/v1/auth/sign-in/start");
        var callback = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/auth/callback");
        callback.setRequestURI("/api/v1/auth/callback");
        var users = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/users");
        users.setRequestURI("/api/v1/users");
        assertThat(filter.shouldNotFilter(signIn)).isTrue();
        assertThat(filter.shouldNotFilter(callback)).isTrue();
        assertThat(filter.shouldNotFilter(users)).isFalse();
    }

    @Test
    @WithTSolveUser
    void preservesExistingCaller_AC_004_17() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(reader);
    }
}
