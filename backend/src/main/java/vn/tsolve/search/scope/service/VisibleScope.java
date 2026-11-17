package vn.tsolve.search.scope.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.tsolve.common.security.TSolveUser;

/** Membership comes from the Workspace database (MembershipVisibleScope), never from request filters. */
public interface VisibleScope {
    Scope of(TSolveUser user);

    /** Internal ids; writerId is the ticket.writer_id of the user's own tickets (the account id). */
    record Scope(Set<Long> departmentIds, Set<Long> managedProjectIds, Optional<UUID> writerId) {
        public Scope {
            departmentIds = Set.copyOf(departmentIds);
            managedProjectIds = Set.copyOf(managedProjectIds);
            writerId = writerId == null ? Optional.empty() : writerId;
        }

        public boolean isEmpty() {
            return departmentIds.isEmpty() && managedProjectIds.isEmpty() && writerId.isEmpty();
        }

        public static Scope empty() {
            return new Scope(Set.of(), Set.of(), Optional.empty());
        }
    }
}
