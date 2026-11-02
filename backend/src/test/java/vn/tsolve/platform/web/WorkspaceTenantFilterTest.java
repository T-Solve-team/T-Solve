package vn.tsolve.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionOperations;
import vn.tsolve.common.tenant.ScopedTenantContext;
import vn.tsolve.platform.config.SqlWorkspaceResources;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalogService;

class WorkspaceTenantFilterTest {
    private final WorkspaceCatalogService catalog = mock(WorkspaceCatalogService.class);
    private final ScopedTenantContext context =
            new ScopedTenantContext(id -> true, TransactionOperations.withoutTransaction());
    private final SqlWorkspaceResources resources =
            new SqlWorkspaceResources(null, null, context, null, catalog, new WorkspaceHost("localhost"));
    private final WorkspaceTenantFilter filter = new WorkspaceTenantFilter(
            () -> resources, Jackson2ObjectMapperBuilder.json().build());

    @Test
    void rejectsUnknownAndSuspendedBeforeHandler_AC_002_5_AC_002_7_AC_002_8_AC_002_9() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request("unknown.localhost"), response, (req, res) -> {
            throw new AssertionError("handler ran");
        });
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"MSG151\"")
                .doesNotContain("unknown");
        when(catalog.findBySiteName("one")).thenReturn(Optional.of(new WorkspaceSummary(1, "one", "SUSPENDED", null)));
        response = new MockHttpServletResponse();
        filter.doFilter(request("one.localhost"), response, (req, res) -> {
            throw new AssertionError("handler ran");
        });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("MSG150");
    }

    @Test
    void ignoresSelectorsAndCleansBindingOnFailure_AC_002_3_AC_002_11() throws Exception {
        when(catalog.findBySiteName("one")).thenReturn(Optional.of(new WorkspaceSummary(1, "one", "ACTIVE", null)));
        var request = request("one.localhost");
        request.addHeader("X-Forwarded-Host", "other.localhost");
        request.addHeader("Forwarded", "host=other.localhost");
        request.addHeader("X-Workspace-Id", "2");
        request.setParameter("workspaceId", "2");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    assertThat(context.requireWorkspaceId()).isEqualTo(1L);
                    throw new ServletException("test failure");
                }))
                .isInstanceOf(ServletException.class);
        assertThat(context.currentWorkspaceId()).isEmpty();
        filter.doFilter(request("one.localhost"), new MockHttpServletResponse(), (req, res) -> assertThat(
                        context.requireWorkspaceId())
                .isEqualTo(1L));
        assertThat(context.currentWorkspaceId()).isEmpty();
    }

    @Test
    void skipsOtherModulesAndFailsClosedWhenDisabled_AC_002_19_AC_002_25() throws Exception {
        var health = request("unused");
        health.setRequestURI("/actuator/health");
        filter.doFilter(health, new MockHttpServletResponse(), (req, res) -> {});
        verifyNoInteractions(catalog);
        var disabled = new WorkspaceTenantFilter(
                () -> null, Jackson2ObjectMapperBuilder.json().build());
        var response = new MockHttpServletResponse();
        disabled.doFilter(request("one.localhost"), response, (req, res) -> {
            throw new AssertionError("handler ran");
        });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("MSG06");
    }

    private static MockHttpServletRequest request(String host) {
        var request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Host", host);
        return request;
    }
}
