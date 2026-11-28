package vn.tsolve.platform.web;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import vn.tsolve.common.security.JiraAppSecurityConfig;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.dto.WorkspaceStatus;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;

/**
 * The web Tenant Resolver (STORY-002): the {@code Host} header alone names the Workspace, which stays bound for the
 * whole request. Runs before every security check, and passes everything through when there is no catalog.
 */
public class TenantResolverFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantResolverFilter.class);
    private static final String RESOLVED_ID = TenantResolverFilter.class.getName() + ".workspaceId";

    // Both matchers read the decoded path, so an encoded one cannot pass here and reach another chain.
    private static final RequestMatcher JIRA_APP = AntPathRequestMatcher.antMatcher(JiraAppSecurityConfig.PATHS);
    /** AC-002.19: served on every host. */
    private static final RequestMatcher ANY_HOST = new OrRequestMatcher(Stream.of(
                    "/actuator/health",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/swagger-ui.html",
                    "/swagger-ui/**")
            .<RequestMatcher>map(AntPathRequestMatcher::antMatcher)
            .toList());

    private final HostClassifier hosts;
    private final ObjectProvider<WorkspaceCatalog> catalog;
    private final ThreadLocalTenantContext holder;
    private final SecurityProblemWriter problems;

    public TenantResolverFilter(
            HostClassifier hosts,
            ObjectProvider<WorkspaceCatalog> catalog,
            ThreadLocalTenantContext holder,
            SecurityProblemWriter problems) {
        this.hosts = hosts;
        this.catalog = catalog;
        this.holder = holder;
        this.problems = problems;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) servletRequest;
        var response = (HttpServletResponse) servletResponse;
        WorkspaceCatalog workspaces = catalog.getIfAvailable();
        if (workspaces == null || request.getDispatcherType() == DispatcherType.ERROR) {
            chain.doFilter(request, response);
            return;
        }
        // An ASYNC re-dispatch keeps the Workspace of its first dispatch, with no second lookup.
        if (request.getDispatcherType() == DispatcherType.ASYNC
                && request.getAttribute(RESOLVED_ID) instanceof UUID resolved) {
            runBound(resolved, request, response, chain);
            return;
        }
        if (ANY_HOST.matches(request)) {
            chain.doFilter(request, response);
            return;
        }

        String host = request.getHeader("Host");
        HostKind kind = hosts.classify(host);
        if (kind instanceof HostKind.NotASite) {
            refuse(request, response, HttpStatus.NOT_FOUND, SecurityProblemWriter.MSG151);
            return;
        }
        UUID workspaceId = null;
        if (kind instanceof HostKind.Site site) {
            Optional<WorkspaceSummary> found;
            try {
                found = workspaces.findBySiteName(site.label());
            } catch (RuntimeException failure) {
                // The class only: the message can hold the Registry's JDBC URL.
                LOG.error(
                        "Workspace lookup failed: host {}, code {}, {}",
                        host,
                        SecurityProblemWriter.MSG06,
                        failure.getClass().getSimpleName());
                problems.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR, SecurityProblemWriter.MSG06);
                return;
            }
            if (found.isEmpty()) {
                refuse(request, response, HttpStatus.NOT_FOUND, SecurityProblemWriter.MSG151);
                return;
            }
            if (found.get().status() == WorkspaceStatus.SUSPENDED) {
                refuse(request, response, HttpStatus.FORBIDDEN, SecurityProblemWriter.MSG150);
                return;
            }
            workspaceId = found.get().id();
        }
        // The Jira App paths live on api. only, and api. serves nothing else (AC-005.7, AC-005.8).
        if (JIRA_APP.matches(request) != (kind instanceof HostKind.Api)) {
            refuse(request, response, HttpStatus.NOT_FOUND, SecurityProblemWriter.MSG06);
            return;
        }
        if (workspaceId == null) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(RESOLVED_ID, workspaceId);
        runBound(workspaceId, request, response, chain);
    }

    private void runBound(UUID workspaceId, ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var binding = holder.bindRequest(workspaceId);
        try {
            chain.doFilter(request, response);
        } finally {
            binding.close();
        }
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        LOG.info("Request refused: host {}, code {}", request.getHeader("Host"), code);
        problems.write(request, response, status, code);
    }
}
