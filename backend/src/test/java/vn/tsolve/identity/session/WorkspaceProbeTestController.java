package vn.tsolve.identity.session;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.signin.port.TenantScope;

/**
 * Test-only protected endpoints that show which Workspace is bound while a request runs (AC-001.76). {@code
 * @TestComponent} keeps it out of component scanning: a test adds it with {@code @Import}.
 */
@RestController
@TestComponent
@RequestMapping("/api/v1/test/workspace")
public class WorkspaceProbeTestController {

    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<UUID> BOUND_WHEN_THROWN = new AtomicReference<>();

    private final TenantScope scope;
    private final AccountRepository accounts;

    public WorkspaceProbeTestController(TenantScope scope, AccountRepository accounts) {
        this.scope = scope;
        this.accounts = accounts;
    }

    public static int calls() {
        return CALLS.get();
    }

    /** The Workspace that was bound when {@code /throw} threw, or null. */
    public static UUID boundWhenThrown() {
        return BOUND_WHEN_THROWN.get();
    }

    public static void reset() {
        CALLS.set(0);
        BOUND_WHEN_THROWN.set(null);
    }

    private List<String> emails() {
        return accounts.findAll().stream().map(Account::getEmail).sorted().toList();
    }

    @GetMapping("/bound")
    public Map<String, Object> bound() {
        CALLS.incrementAndGet();
        return Map.of("workspaceId", scope.currentWorkspaceId().toString(), "emails", emails());
    }

    @GetMapping("/throw")
    public Map<String, Object> thrown() {
        CALLS.incrementAndGet();
        BOUND_WHEN_THROWN.set(scope.currentWorkspaceId());
        throw new RuntimeException("the endpoint failed");
    }

    @GetMapping("/callable")
    public Callable<Map<String, Object>> callable() {
        CALLS.incrementAndGet();
        return () -> Map.of("workspaceId", scope.currentWorkspaceId().toString(), "emails", emails());
    }
}
