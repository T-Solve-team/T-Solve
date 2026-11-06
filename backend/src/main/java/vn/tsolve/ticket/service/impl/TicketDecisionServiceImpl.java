package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.entity.ReviewDecisionRecord;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.ReviewSourceNotEligibleException;
import vn.tsolve.ticket.repository.ReviewDecisionRecordRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.TicketDecisionService;

@Service
public class TicketDecisionServiceImpl implements TicketDecisionService {

    private static final int MAX_TICKETS = 500;
    private static final int MAX_FEEDBACK_LENGTH = 1000;

    private final TicketRepository tickets;
    private final ReviewDecisionRecordRepository decisions;
    private final Clock clock;

    public TicketDecisionServiceImpl(TicketRepository tickets, ReviewDecisionRecordRepository decisions, Clock clock) {
        this.tickets = tickets;
        this.decisions = decisions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int decide(
            UUID reviewerId, Set<Long> managedProjectIds, List<UUID> ticketIds, Action action, String feedback) {
        Objects.requireNonNull(reviewerId, "Verified reviewer is required");
        Objects.requireNonNull(action, "Decision action is required");
        if (ticketIds == null || ticketIds.isEmpty() || ticketIds.size() > MAX_TICKETS) {
            throw new IllegalArgumentException("A decision requires 1 to 500 ticket IDs");
        }
        Set<UUID> ids = new HashSet<>(ticketIds);
        if (ids.contains(null) || ids.size() != ticketIds.size()) {
            throw new IllegalArgumentException("Ticket IDs must be non-null and unique");
        }
        String text = validatedFeedback(action, feedback);
        if (managedProjectIds.isEmpty()) {
            throw new ProjectAccessDeniedException();
        }

        List<Ticket> batch = tickets.lockForReview(ids);
        validateBatch(batch, ids.size(), managedProjectIds, action);

        TicketStatus outcome =
                switch (action) {
                    case APPROVED -> TicketStatus.PUBLISHED;
                    case REJECTED -> TicketStatus.REJECTED;
                    case CHANGES_REQUESTED -> TicketStatus.CHANGES_REQUESTED;
                };
        Instant now = clock.instant();
        List<ReviewDecisionRecord> history = new ArrayList<>(batch.size());
        for (Ticket ticket : batch) {
            ticket.applyReview(outcome, text, now);
            history.add(new ReviewDecisionRecord(ticket.getId(), reviewerId, action.name(), text, now));
        }
        decisions.saveAll(history);
        return batch.size();
    }

    private static String validatedFeedback(Action action, String feedback) {
        if (action == Action.APPROVED) {
            return null;
        }
        if (feedback == null || feedback.isBlank() || feedback.length() > MAX_FEEDBACK_LENGTH) {
            throw new IllegalArgumentException("Decision feedback must contain 1 to 1000 characters");
        }
        return feedback.strip();
    }

    private static void validateBatch(
            List<Ticket> batch, int expectedSize, Set<Long> managedProjectIds, Action action) {
        if (batch.size() != expectedSize) {
            throw new NotFoundException("MSG04", Map.of());
        }
        if (batch.stream().anyMatch(ticket -> !managedProjectIds.contains(ticket.getProjectId()))) {
            throw new ProjectAccessDeniedException();
        }
        boolean containsImportedTicket =
                batch.stream().anyMatch(ticket -> ticket.getSource() != TicketSource.SOLUTION_FORM);
        if (containsImportedTicket && action == Action.CHANGES_REQUESTED) {
            throw new ReviewSourceNotEligibleException();
        }
        if (containsImportedTicket
                || batch.stream().anyMatch(ticket -> ticket.getStatus() != TicketStatus.PENDING_REVIEW)) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
    }
}
