package vn.tsolve.identity.signin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.identity.account.Account;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.signin.port.RecordingTenantScope;

/**
 * An in-memory {@link AccountRepository}: rows are kept per Workspace and chosen by the bound Workspace of the
 * {@link RecordingTenantScope}. A call with no Workspace bound throws, as the real tenant data source must, and the
 * Workspace of every call is recorded. {@code findByEmail} compares exactly (the caller lower-cases).
 */
public final class AccountStore implements Answer<Object> {

    private final RecordingTenantScope scope;
    private final Map<UUID, List<Account>> rows = new LinkedHashMap<>();
    private final List<UUID> callWorkspaces = new CopyOnWriteArrayList<>();
    private final AccountRepository repository;
    private volatile boolean failOnSave;

    public AccountStore(RecordingTenantScope scope) {
        this.scope = scope;
        this.repository = Mockito.mock(AccountRepository.class, this);
    }

    public AccountRepository repository() {
        return repository;
    }

    /** Puts an Account into one Workspace's database. */
    public synchronized Account seed(UUID workspaceId, Account account) {
        if (account.getId() == null) {
            ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        }
        rows.computeIfAbsent(workspaceId, key -> new ArrayList<>()).add(account);
        return account;
    }

    /** The stored Account of this Workspace, read without going through the repository double. */
    public synchronized Optional<Account> find(UUID workspaceId, String email) {
        return rows.getOrDefault(workspaceId, List.of()).stream()
                .filter(account -> account.getEmail().equals(email))
                .findFirst();
    }

    /** The bound Workspace of every repository call since the last reset. */
    public List<UUID> callWorkspaces() {
        return List.copyOf(callWorkspaces);
    }

    /** From now on {@code save} and {@code saveAndFlush} throw, as a failing database would. */
    public void failOnSave() {
        failOnSave = true;
    }

    public synchronized void reset() {
        rows.clear();
        callWorkspaces.clear();
        failOnSave = false;
        Mockito.reset(repository);
    }

    @Override
    public synchronized Object answer(InvocationOnMock invocation) throws Throwable {
        switch (invocation.getMethod().getName()) {
            case "findByEmail" -> {
                UUID workspace = bound();
                String email = invocation.getArgument(0);
                return rows.getOrDefault(workspace, List.of()).stream()
                        .filter(account -> account.getEmail().equals(email))
                        .findFirst();
            }
            case "findById" -> {
                UUID workspace = bound();
                return rows.getOrDefault(workspace, List.of()).stream()
                        .filter(account -> account.getId().equals(invocation.getArgument(0)))
                        .findFirst();
            }
            case "save", "saveAndFlush" -> {
                UUID workspace = bound();
                if (failOnSave) {
                    throw new IllegalStateException("database is down");
                }
                Account account = invocation.getArgument(0);
                if (account.getId() == null) {
                    ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
                }
                List<Account> list = rows.computeIfAbsent(workspace, key -> new ArrayList<>());
                if (!list.contains(account)) {
                    list.add(account);
                }
                return account;
            }
            default -> {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        }
    }

    private UUID bound() {
        UUID workspace = scope.currentWorkspaceId();
        callWorkspaces.add(workspace);
        return workspace;
    }
}
