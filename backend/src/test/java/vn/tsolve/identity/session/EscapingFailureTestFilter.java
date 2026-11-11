package vn.tsolve.identity.session;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Test-only: a servlet filter after the security chain, on {@code /api/v1/test/workspace/escape} only. It records the
 * Workspace bound at that point, then throws a {@link ServletException}. A controller exception never leaves the chain
 * ({@code GlobalExceptionHandler} answers it), so only a failure like this one reaches the {@code WebSessionFilter}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EscapingFailureTestFilter {

    private static final AtomicReference<UUID> BOUND = new AtomicReference<>();
    private static final AtomicReference<ServletException> THROWN = new AtomicReference<>();

    public static UUID boundWhenThrown() {
        return BOUND.get();
    }

    public static ServletException thrown() {
        return THROWN.get();
    }

    public static void reset() {
        BOUND.set(null);
        THROWN.set(null);
    }

    @Bean
    FilterRegistrationBean<Filter> escapingFailureRegistration(TenantScope scope) {
        Filter filter = new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                try {
                    BOUND.set(scope.currentWorkspaceId());
                } catch (IllegalStateException e) {
                    BOUND.set(null); // nothing is bound here
                }
                ServletException failure = new ServletException("escape");
                THROWN.set(failure);
                throw failure;
            }
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/test/workspace/escape");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
