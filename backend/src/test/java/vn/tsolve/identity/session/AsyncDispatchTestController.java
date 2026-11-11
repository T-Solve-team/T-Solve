package vn.tsolve.identity.session;

import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.common.security.TSolveUser;

/**
 * Test-only protected endpoint that answers from a {@link Callable}, so the container re-dispatches the request (ASYNC)
 * when the Callable returns. A test adds it with {@code @Import(AsyncDispatchTestController.class)};
 * {@code @TestComponent} keeps it out of component scanning.
 */
@RestController
@TestComponent
@RequestMapping("/api/v1/test")
public class AsyncDispatchTestController {

    @GetMapping("/async")
    public Callable<Map<String, Object>> async() {
        String email = TSolveUser.current().orElseThrow().email();
        return () -> Map.of("email", email);
    }
}
