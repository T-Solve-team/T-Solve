package vn.tsolve.identity.signin.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Three fixed Workspaces. Hosts match exactly (lower case, no port); every host asked for is recorded so a test
 * can show a host was never looked up.
 */
public final class InMemoryWorkspaceLookup implements WorkspaceLookup {

    public static final WorkspaceRef ACME = new WorkspaceRef(
            UUID.fromString("00000000-0000-4000-8000-00000000000a"),
            "acme.localhost",
            "acme.example",
            WorkspaceRef.Status.ACTIVE);

    public static final WorkspaceRef BETA = new WorkspaceRef(
            UUID.fromString("00000000-0000-4000-8000-00000000000b"),
            "beta.localhost",
            "beta.example",
            WorkspaceRef.Status.ACTIVE);

    public static final WorkspaceRef GAMMA = new WorkspaceRef(
            UUID.fromString("00000000-0000-4000-8000-00000000000c"),
            "gamma.localhost",
            "gamma.example",
            WorkspaceRef.Status.SUSPENDED);

    private static final List<WorkspaceRef> WORKSPACES = List.of(ACME, BETA, GAMMA);

    private final List<String> askedHosts = new CopyOnWriteArrayList<>();

    @Override
    public Optional<WorkspaceRef> findBySiteHost(String host) {
        askedHosts.add(host);
        return WORKSPACES.stream()
                .filter(workspace -> workspace.siteHost().equals(host))
                .findFirst();
    }

    @Override
    public Optional<WorkspaceRef> findById(UUID id) {
        return WORKSPACES.stream()
                .filter(workspace -> workspace.id().equals(id))
                .findFirst();
    }

    public List<String> askedHosts() {
        return List.copyOf(askedHosts);
    }

    public void reset() {
        askedHosts.clear();
    }
}
