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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.entity.ReviewDecisionRecord;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
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
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public TicketDecisionServiceImpl(
            TicketRepository tickets,
            ReviewDecisionRecordRepository decisions,
            Clock clock,
            ApplicationEventPublisher events,
            AuditService audit) {
        this.tickets = tickets;
        this.decisions = decisions;
        this.clock = clock;
        this.events = events;
        this.audit = audit;
    }

    @Override
    @Transactional
    public int decide(
            UUID reviewerId, Set<UUID> managedProjectIds, List<UUID> ticketIds, Action action, String feedback) {
        Objects.requireNonNull(reviewerId, "Verified reviewer is required");
        Objects.requireNonNull(action, "Decision action is required");
        if (ticketIds == null || ticketIds.isEmpty() || ticketIds.size() > MAX_TICKETS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Set<UUID> ids = new HashSet<>(ticketIds);
        if (ids.contains(null) || ids.size() != ticketIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
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
        List<ReviewDecisionRecord> saved = decisions.saveAll(history);
        AuditAction auditAction =
                switch (action) {
                    case APPROVED -> AuditAction.APPROVE;
                    case REJECTED -> AuditAction.REJECT;
                    case CHANGES_REQUESTED -> AuditAction.REQUEST_CHANGES;
                };
        for (Ticket ticket : batch) {
            audit.record(auditAction, "TICKET", ticket.getId());
        }
        if (action != Action.APPROVED) {
            // Consumed after commit (STORY-010): the writer email never runs inside this transaction.
            events.publishEvent(new ReviewFeedbackDecidedEvent(
                    saved.stream().map(ReviewDecisionRecord::getId).toList()));
        }
        return batch.size();
    }

    private static String validatedFeedback(Action action, String feedback) {
        if (action == Action.APPROVED) {
            return null;
        }
        if (feedback == null || feedback.isBlank() || feedback.length() > MAX_FEEDBACK_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return feedback.strip();
    }

    private static void validateBatch(
            List<Ticket> batch, int expectedSize, Set<UUID> managedProjectIds, Action action) {
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
