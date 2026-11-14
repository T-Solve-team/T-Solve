package vn.tsolve.ticket.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TicketStatus {
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    CHANGES_REQUESTED,
    UNPUBLISHED,
    EXPIRED;

    /** The status changes a Ticket may make (STORY-005, D9). Rejected is final. */
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED.put(PENDING_REVIEW, EnumSet.of(PUBLISHED, REJECTED, CHANGES_REQUESTED, EXPIRED));
        ALLOWED.put(CHANGES_REQUESTED, EnumSet.of(PENDING_REVIEW, EXPIRED));
        ALLOWED.put(EXPIRED, EnumSet.of(PENDING_REVIEW));
        ALLOWED.put(PUBLISHED, EnumSet.of(UNPUBLISHED));
        ALLOWED.put(UNPUBLISHED, EnumSet.of(PUBLISHED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(TicketStatus.class));
    }

    public boolean canMoveTo(TicketStatus to) {
        return ALLOWED.get(this).contains(to);
    }
}
