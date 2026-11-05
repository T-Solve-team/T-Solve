package vn.tsolve.identity.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.identity.exception.UserListException;
import vn.tsolve.identity.service.UserListAccessService;
import vn.tsolve.identity.service.impl.UserListTransaction;

public final class UserListSessionFilter extends OncePerRequestFilter {
    private final UserListTransaction database;
    private final UserListAccessService access;
    private final SecurityProblemWriter problems;

    public UserListSessionFilter(
            UserListTransaction database, UserListAccessService access, SecurityProblemWriter problems) {
        this.database = database;
        this.access = access;
        this.problems = problems;
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
            var live = new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(),
                    null,
                    roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .toList());
            live.setDetails(authentication.getDetails());
            current.setAuthentication(live);
            SecurityContextHolder.setContext(current);
            chain.doFilter(request, response);
        } catch (UserListException failure) {
            problems.write(request, response, failure.getStatus(), failure.getCode());
        } finally {
            // Do not mutate/persist the session's original context while rebuilding live database authorities.
            SecurityContextHolder.setContext(original);
        }
    }
}
