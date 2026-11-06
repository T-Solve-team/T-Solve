package vn.tsolve.review.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReviewWorkspaceAccess {

    /** Resolve the signed-in Account in the already bound Workspace. Empty means the binding or Account is unverified. */
    Optional<Set<Long>> managedProjectIds(UUID accountId);
}
