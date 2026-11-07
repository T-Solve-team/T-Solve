package vn.tsolve.search.feedback.service.impl;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.feedback.dto.TicketFeedbackRequest;
import vn.tsolve.search.feedback.repository.FeedbackTicketRepository;
import vn.tsolve.search.feedback.repository.TicketFeedbackRepository;
import vn.tsolve.search.feedback.service.TicketFeedbackService;
import vn.tsolve.search.scope.service.VisibleScope;

@Service
public class TicketFeedbackServiceImpl implements TicketFeedbackService {
    private final FeedbackTicketRepository tickets;
    private final TicketFeedbackRepository feedback;
    private final VisibleScope visibleScope;

    public TicketFeedbackServiceImpl(
            FeedbackTicketRepository tickets, TicketFeedbackRepository feedback, VisibleScope visibleScope) {
        this.tickets = tickets;
        this.feedback = feedback;
        this.visibleScope = visibleScope;
    }

    @Override
    @Transactional
    public void give(TSolveUser user, UUID ticketId, TicketFeedbackRequest request) {
        VisibleScope.Scope scope = visibleScope.of(user);
        if (scope.isEmpty()) throw new NotFoundException("MSG04", Map.of());
        var ticket = tickets.findByPublicId(ticketId)
                .filter(value -> "PUBLISHED".equals(value.getStatus()))
                .filter(value -> "ACTIVE".equals(value.getDepartmentStatus()))
                .filter(value -> scope.departmentIds().contains(value.getDepartmentId()))
                .orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        String comment = request.comment() == null ? null : request.comment().trim();
        if (comment != null && comment.isBlank()) comment = null;
        feedback.upsert(ticket.getId(), user.id(), request.isHelpful() ? "HELPFUL" : "NOT_HELPFUL", comment, "WEB");
    }
}
