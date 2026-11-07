package vn.tsolve.identity.dev;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;
import vn.tsolve.identity.signin.port.WorkspaceRef.Status;

/** Dev-profile lookup: the two dev Workspaces, matched on the exact lower-case host with no port. */
@Profile("dev")
@Component
public class DevWorkspaceLookup implements WorkspaceLookup {

    private static final List<WorkspaceRef> WORKSPACES = List.of(
            new WorkspaceRef(
                    DevAccounts.ACME_ID,
                    "acme.localhost",
                    "acme.test",
                    Status.ACTIVE,
                    DevAccounts.ACME_SLUG,
                    DevAccounts.ACME_ORGANIZATION_ID),
            new WorkspaceRef(
                    DevAccounts.BETA_ID,
                    "beta.localhost",
                    "beta.test",
                    Status.ACTIVE,
                    DevAccounts.BETA_SLUG,
                    DevAccounts.BETA_ORGANIZATION_ID));

    @Override
    public Optional<WorkspaceRef> findBySiteHost(String host) {
        return WORKSPACES.stream().filter(w -> w.siteHost().equals(host)).findFirst();
    }

    @Override
    public Optional<WorkspaceRef> findById(UUID id) {
        return WORKSPACES.stream().filter(w -> w.id().equals(id)).findFirst();
    }
}
