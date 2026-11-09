package vn.tsolve.ticket.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TicketDecisionService {

    enum Action {
        APPROVED,
        REJECTED,
        CHANGES_REQUESTED
    }

    int sendBack(UUID reviewerId, Set<UUID> managedProjectIds, List<UUID> ticketIds);

    /** The caller supplies a verified Account and its managed Projects in the bound Workspace. */
    int decide(UUID reviewerId, Set<UUID> managedProjectIds, List<UUID> ticketIds, Action action, String feedback);
}
