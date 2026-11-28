package vn.tsolve.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.config.MethodSecurityConfig;
import vn.tsolve.common.config.WebConfig;
import vn.tsolve.common.security.SecurityConfig;
import vn.tsolve.common.tenant.KnownWorkspaces;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.config.TenantFilterConfig;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/** Task 10a (KAN-258): the Tenant Resolver answers before Spring Security does. */
@WebMvcTest(TenantFilterOrderTest.Probe.class)
@Import({
    SecurityConfig.class,
    MethodSecurityConfig.class,
    WebConfig.class,
    TenantFilterConfig.class,
    ThreadLocalTenantContext.class,
    TenantFilterOrderTest.Workspaces.class,
    TenantFilterOrderTest.Probe.class
})
@TestPropertySource(
        properties = {
            "server.port=0",
            "CORS_ALLOWED_ORIGINS=https://client.example.test",
            "TSOLVE_SITE_ORIGIN_TEMPLATE=https://{slug}.tsolve.io.vn"
        })
class TenantFilterOrderTest {

    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FilterRegistrationBean<TenantResolverFilter> registration;

    @Autowired
    private ThreadLocalTenantContext holder;

    @MockBean
    private WorkspaceCatalog catalog;

    @RestController
    static class Probe {
        @PostMapping("/api/probe")
        String write() {
            return "ok";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Workspaces {
        @Bean
        KnownWorkspaces knownWorkspaces() {
            return A::equals;
        }
    }

    @AfterEach
    void nothingIsLeftBound() {
        assertThat(holder.currentWorkspaceId()).as("binding left on the thread").isEmpty();
    }

    @Test
    @DisplayName(
            "AC-002.8 edge 18 runs before Spring Security, so MSG151 and MSG150 come before the CSRF and session checks")
    void runsBeforeSpringSecurity_AC_002_8() throws Exception {
        assertThat(registration.getOrder()).isLessThan(SecurityProperties.DEFAULT_FILTER_ORDER);
        assertThat(dispatcherTypes()).isEqualTo(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));

        mvc.perform(anonymousPost("nosuch.tsolve.io.vn"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG151"));

        when(catalog.findBySiteName("acme")).thenReturn(acme(WorkspaceStatus.SUSPENDED));
        mvc.perform(anonymousPost("acme.tsolve.io.vn"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG150"));

        when(catalog.findBySiteName("acme")).thenReturn(acme(WorkspaceStatus.ACTIVE));
        mvc.perform(anonymousPost("acme.tsolve.io.vn"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MSG07"));
    }

    /** The dispatcher types the registration really maps, defaults included. */
    private EnumSet<DispatcherType> dispatcherTypes() throws ServletException {
        ServletContext servletContext = mock(ServletContext.class);
        FilterRegistration.Dynamic dynamic = mock(FilterRegistration.Dynamic.class);
        when(servletContext.addFilter(anyString(), any(Filter.class))).thenReturn(dynamic);

        registration.onStartup(servletContext);

        ArgumentCaptor<EnumSet<DispatcherType>> types = ArgumentCaptor.captor();
        verify(dynamic).addMappingForUrlPatterns(types.capture(), anyBoolean(), any(String[].class));
        return types.getValue();
    }

    /** No CSRF token and no session. */
    private static MockHttpServletRequestBuilder anonymousPost(String host) {
        return post("/api/probe").servletPath("/api/probe").header("Host", host);
    }

    private static Optional<WorkspaceSummary> acme(WorkspaceStatus status) {
        return Optional.of(new WorkspaceSummary(
                A, "acme", status, "acme.example", Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
