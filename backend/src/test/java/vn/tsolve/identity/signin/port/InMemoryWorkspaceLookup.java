package vn.tsolve.identity.signin.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fixed Workspaces. Hosts match exactly (lower case, no port); every host and id asked for is recorded so a test can
 * show one was never looked up. A test may remove or replace a Workspace; {@link #reset()} restores all.
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

    /** No Google domain set (null). */
    public static final WorkspaceRef NO_DOMAIN = new WorkspaceRef(
            UUID.fromString("00000000-0000-4000-8000-00000000000d"),
            "nodomain.localhost",
            null,
            WorkspaceRef.Status.ACTIVE);

    /** A blank Google domain. */
    public static final WorkspaceRef BLANK_DOMAIN = new WorkspaceRef(
            UUID.fromString("00000000-0000-4000-8000-00000000000e"),
            "blankdomain.localhost",
            "  ",
            WorkspaceRef.Status.ACTIVE);

    private static final List<WorkspaceRef> ALL = List.of(ACME, BETA, GAMMA, NO_DOMAIN, BLANK_DOMAIN);

    private final List<WorkspaceRef> workspaces = new CopyOnWriteArrayList<>(ALL);
    private final List<String> askedHosts = new CopyOnWriteArrayList<>();
    private final List<UUID> askedIds = new CopyOnWriteArrayList<>();

    @Override
    public Optional<WorkspaceRef> findBySiteHost(String host) {
        askedHosts.add(host);
        return workspaces.stream()
                .filter(workspace -> workspace.siteHost().equals(host))
                .findFirst();
    }

    @Override
    public Optional<WorkspaceRef> findById(UUID id) {
        askedIds.add(id);
        return workspaces.stream()
                .filter(workspace -> workspace.id().equals(id))
                .findFirst();
    }

    public List<String> askedHosts() {
        return List.copyOf(askedHosts);
    }

    public List<UUID> askedIds() {
        return List.copyOf(askedIds);
    }

    /** The Workspace is gone (deleted after a sign-in state was made for it). */
    public void remove(UUID id) {
        workspaces.removeIf(workspace -> workspace.id().equals(id));
    }

    /** Swaps the Workspace with the same id, e.g. to clear its Google domain after a state was made. */
    public void replace(WorkspaceRef replacement) {
        remove(replacement.id());
        workspaces.add(replacement);
    }

    public void reset() {
        askedHosts.clear();
        askedIds.clear();
        workspaces.clear();
        workspaces.addAll(ALL);
    }
}
