package vn.tsolve.identity.signin.port;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stand-in until the platform adapter {@code PlatformWorkspaceLookup} exists: it knows no Workspace, so a sign-in
 * start on a Workspace site answers 404. The adapter replaces this class.
 */
@Component
public class NoWorkspaceLookup implements WorkspaceLookup {

    @Override
    public Optional<WorkspaceRef> findBySiteHost(String host) {
        return Optional.empty();
    }

    @Override
    public Optional<WorkspaceRef> findById(UUID id) {
        return Optional.empty();
    }
}
