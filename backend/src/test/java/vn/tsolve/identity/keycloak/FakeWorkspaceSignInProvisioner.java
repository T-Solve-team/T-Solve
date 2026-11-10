package vn.tsolve.identity.keycloak;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner;

/**
 * Test-only fake of the port for the platform's own tests (Task 14): records every call, returns a fixed Organization
 * id, and can be told to throw. It touches no Keycloak. Not a test.
 */
public final class FakeWorkspaceSignInProvisioner implements WorkspaceSignInProvisioner {

    public static final String ORGANIZATION_ID = "10000000-0000-4000-8000-0000000000ff";

    public record ProvisionCall(UUID workspaceId, String siteName, String googleDomain) {}

    private final List<ProvisionCall> provisioned = new CopyOnWriteArrayList<>();
    private final List<UUID> removed = new CopyOnWriteArrayList<>();
    private final List<UUID> checked = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    /** Every later call throws this exception until {@link #succeed()}. */
    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public void succeed() {
        this.failure = null;
    }

    public List<ProvisionCall> provisioned() {
        return new ArrayList<>(provisioned);
    }

    public List<UUID> removed() {
        return new ArrayList<>(removed);
    }

    public List<UUID> checked() {
        return new ArrayList<>(checked);
    }

    @Override
    public Optional<String> provision(UUID workspaceId, String siteName, String googleDomain) {
        provisioned.add(new ProvisionCall(workspaceId, siteName, googleDomain));
        throwIfFailing();
        return Optional.of(ORGANIZATION_ID);
    }

    @Override
    public ProvisionedObjects check(UUID workspaceId) {
        checked.add(workspaceId);
        throwIfFailing();
        boolean exists = provisioned.stream().anyMatch(c -> c.workspaceId().equals(workspaceId))
                && !removed.contains(workspaceId);
        return new ProvisionedObjects(exists, exists, exists, exists);
    }

    @Override
    public void remove(UUID workspaceId) {
        removed.add(workspaceId);
        throwIfFailing();
    }

    private void throwIfFailing() {
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
    }
}
