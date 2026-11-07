package vn.tsolve.identity.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tsolve.identity.signin.port.TenantScope;

/** Interim mock — replace when KAN-275 workspace-session (identity Tasks 26a/26b) lands; never enable in prod. */
@Profile("identity-mock")
public class MockSignedInCallerFilter extends OncePerRequestFilter {
    private final TenantScope scope;
    private final MockCallerReader reader;
    private final MockIdentityProperties properties;

    public MockSignedInCallerFilter(TenantScope scope, MockCallerReader reader, MockIdentityProperties properties) {
        this.scope = scope;
        this.reader = reader;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI()
                        .substring(request.getContextPath().length())
                        .startsWith("/api/")
                || new AuthenticationTrustResolverImpl()
                        .isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            scope.runIn(properties.workspaceId(), () -> {
                var user = reader.read(properties.accountEmail());
                String attribute = RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME;
                Object previous = request.getAttribute(attribute);
                if (user.isPresent()) {
                    var caller = user.get();
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(caller, null, caller.authorities()));
                    // SecurityContextHolderFilter reloads from the default repository; the holder alone is
                    // insufficient.
                    request.setAttribute(attribute, context);
                    SecurityContextHolder.setContext(context);
                }
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new FilterChainFailure(e);
                } finally {
                    if (user.isPresent()) {
                        SecurityContextHolder.clearContext();
                        if (previous == null) {
                            request.removeAttribute(attribute);
                        } else {
                            request.setAttribute(attribute, previous);
                        }
                    }
                }
                return null;
            });
        } catch (FilterChainFailure e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) e.getCause();
        }
    }

    private static final class FilterChainFailure extends RuntimeException {
        private FilterChainFailure(Exception cause) {
            super(cause);
        }
    }
}
