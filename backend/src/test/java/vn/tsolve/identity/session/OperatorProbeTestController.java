package vn.tsolve.identity.session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Test-only protected endpoint that shows what an Operator session looks like inside a request (Task 29b): the simple
 * class name of the principal (as a string, so no test imports a production type that does not exist yet), the
 * {@code Authentication} name, the authority names, and whether a Workspace is bound. It counts its calls. Add it with
 * {@code @Import(OperatorProbeTestController.class)}.
 */
@RestController
@TestComponent
@RequestMapping("/api/v1/test/operator")
public class OperatorProbeTestController {

    private static final AtomicInteger CALLS = new AtomicInteger();

    private final TenantScope scope;

    public OperatorProbeTestController(TenantScope scope) {
        this.scope = scope;
    }

    public static int calls() {
        return CALLS.get();
    }

    public static void reset() {
        CALLS.set(0);
    }

    @GetMapping
    public Map<String, Object> probe() {
        CALLS.incrementAndGet();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication.getPrincipal();
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        boolean bound;
        try {
            scope.currentWorkspaceId();
            bound = true;
        } catch (RuntimeException e) {
            bound = false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("principalClass", principal.getClass().getSimpleName());
        body.put("name", authentication.getName());
        body.put("authorities", authorities);
        body.put("workspaceBound", bound);
        return body;
    }
}
