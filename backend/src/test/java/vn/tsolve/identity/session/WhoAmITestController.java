package vn.tsolve.identity.session;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.TSolveUser;

/**
 * Test-only protected endpoint on the shared chain ({@code /api/**} needs a signed-in user). It counts its calls so a
 * test can show the endpoint did not run, and answers the {@link TSolveUser} it sees.
 *
 * <p>{@code @TestComponent} keeps it out of component scanning (Spring Boot's test type filter skips it): a test adds
 * it with {@code @Import(WhoAmITestController.class)}, so no other test context gets the endpoint.
 */
@RestController
@TestComponent
@RequestMapping("/api/v1/test")
public class WhoAmITestController {

    private static final AtomicInteger CALLS = new AtomicInteger();

    public static int calls() {
        return CALLS.get();
    }

    public static void reset() {
        CALLS.set(0);
    }

    @GetMapping("/whoami")
    public Map<String, Object> whoami() {
        CALLS.incrementAndGet();
        TSolveUser user = TSolveUser.current().orElseThrow(() -> new IllegalStateException("no TSolveUser"));
        List<String> roles = user.roles().stream().map(Enum::name).sorted().toList();
        return Map.of("id", user.id().toString(), "email", user.email(), "fullName", user.fullName(), "roles", roles);
    }
}
