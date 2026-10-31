package vn.tsolve.identity.signin.port;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceLookup {

    Optional<WorkspaceRef> findBySiteHost(String host);

    Optional<WorkspaceRef> findById(UUID id);
}
