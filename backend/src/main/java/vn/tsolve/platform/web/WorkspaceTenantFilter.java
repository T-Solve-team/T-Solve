package vn.tsolve.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tsolve.common.exception.ProblemDetails;
import vn.tsolve.common.tenant.TenantAccessException;
import vn.tsolve.platform.config.SqlWorkspaceResources;

public final class WorkspaceTenantFilter extends OncePerRequestFilter {
    private final Supplier<SqlWorkspaceResources> resources;
    private final ObjectMapper mapper;

    public WorkspaceTenantFilter(Supplier<SqlWorkspaceResources> resources, ObjectMapper mapper) {
        this.resources = resources;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/api/v1/users") && !path.startsWith("/api/v1/users/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SqlWorkspaceResources sql = resources.get();
        if (sql == null) {
            problem(response, HttpStatus.SERVICE_UNAVAILABLE, "MSG06");
            return;
        }
        var hostHeaders = Collections.list(request.getHeaders("Host"));
        var site = hostHeaders.size() == 1
                ? sql.hosts().siteName(hostHeaders.getFirst())
                : java.util.Optional.<String>empty();
        if (site.isEmpty()) {
            problem(response, HttpStatus.NOT_FOUND, "MSG151");
            return;
        }
        try {
            var workspace = sql.catalog().findBySiteName(site.get());
            if (workspace.isEmpty()) {
                problem(response, HttpStatus.NOT_FOUND, "MSG151");
                return;
            }
            if (!"ACTIVE".equals(workspace.get().status())) {
                problem(response, HttpStatus.FORBIDDEN, "MSG150");
                return;
            }
            sql.context().withWorkspace(workspace.get().id(), () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException failure) {
                    throw new FilterFailure(failure);
                }
                return null;
            });
        } catch (FilterFailure failure) {
            if (failure.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) failure.getCause();
        } catch (TenantAccessException | DataAccessException failure) {
            if (response.isCommitted()) {
                throw new ServletException("SQL Workspace request failed");
            }
            problem(response, HttpStatus.INTERNAL_SERVER_ERROR, "MSG06");
        }
    }

    private void problem(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ProblemDetails.of(status, code, Map.of()));
    }

    private static final class FilterFailure extends RuntimeException {
        private FilterFailure(Exception cause) {
            super(cause);
        }
    }
}
