package vn.tsolve.review.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;

public interface ReviewWorkspaceAccess {

    /** Resolve the signed-in Account in the already bound Workspace. Empty means the binding or Account is unverified. */
    Optional<Set<Long>> managedProjectIds(UUID accountId);

    /** Projects and their permissions for the verified Account in the bound Workspace, keyed by database ID. */
    default Optional<Map<Long, ProjectAccess>> readableProjects(UUID accountId) {
        return Optional.empty();
    }
}
