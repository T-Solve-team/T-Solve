package vn.tsolve.common.session.lookup;

import java.util.List;
import java.util.UUID;
import vn.tsolve.common.session.dto.ProjectMembership;

/**
 * Implemented by identity. Returns what identity's ScopeAccess counts: a {@code PROJECT_MANAGER} row only for a user
 * with the Role PROJECT_MANAGER, a {@code MEMBER} row only with TICKET_RESOLVER, any Project status. Ordered by
 * {@code jiraProjectKey}, so the {@code departments} list built from it has a stable order.
 */
public interface ProjectMembershipLookup {
    List<ProjectMembership> findProjects(UUID userId);
}
