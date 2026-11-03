package vn.tsolve.identity.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tsolve.common.exception.ProblemDetails;
import vn.tsolve.common.tenant.TenantDatabase;
import vn.tsolve.identity.exception.UserListException;
import vn.tsolve.identity.service.UserListAccessService;

public final class UserListSessionFilter extends OncePerRequestFilter {
    private final TenantDatabase database;
    private final UserListAccessService access;
    private final ObjectMapper mapper;

    public UserListSessionFilter(TenantDatabase database, UserListAccessService access, ObjectMapper mapper) {
        this.database = database;
        this.access = access;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var original = SecurityContextHolder.getContext();
        try {
            var authentication = original.getAuthentication();
            access.validateSession(authentication);
            var roles = database.read(() -> access.requireAdmin(authentication));
            var current = SecurityContextHolder.createEmptyContext();
            current.setAuthentication(new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(),
                    null,
                    roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .toList()));
            SecurityContextHolder.setContext(current);
            chain.doFilter(request, response);
        } catch (UserListException failure) {
            response.setStatus(failure.getStatus().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            mapper.writeValue(
                    response.getOutputStream(),
                    ProblemDetails.of(failure.getStatus(), failure.getCode(), failure.getParams()));
        } finally {
            // Do not mutate/persist the session's original context while rebuilding live database authorities.
            SecurityContextHolder.setContext(original);
        }
    }
}
