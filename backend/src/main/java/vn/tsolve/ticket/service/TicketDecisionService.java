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

    /** The caller supplies a verified Account and its managed Projects in the bound Workspace. */
    int decide(UUID reviewerId, Set<Long> managedProjectIds, List<UUID> ticketIds, Action action, String feedback);
}
