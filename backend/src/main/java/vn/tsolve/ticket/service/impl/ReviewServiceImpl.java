package vn.tsolve.ticket.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.ReviewAction;
import vn.tsolve.ticket.dto.ReviewActor;
import vn.tsolve.ticket.dto.ReviewDecisionResult;
import vn.tsolve.ticket.dto.ReviewQueueItem;
import vn.tsolve.ticket.dto.ReviewRegistration;
import vn.tsolve.ticket.dto.ReviewSource;
import vn.tsolve.ticket.dto.ReviewStatus;
import vn.tsolve.ticket.dto.ReviewTicketResponse;
import vn.tsolve.ticket.entity.ReviewDecision;
import vn.tsolve.ticket.entity.ReviewNotification;
import vn.tsolve.ticket.entity.ReviewTicket;
import vn.tsolve.ticket.exception.ReviewException;
import vn.tsolve.ticket.mapper.ReviewMapper;
import vn.tsolve.ticket.repository.ReviewDecisionRepository;
import vn.tsolve.ticket.repository.ReviewNotificationRepository;
import vn.tsolve.ticket.repository.ReviewTicketRepository;
import vn.tsolve.ticket.service.AuditService;
import vn.tsolve.ticket.service.ReviewAccessService;
import vn.tsolve.ticket.service.ReviewService;

@Service
public class ReviewServiceImpl implements ReviewService {
    private final ObjectProvider<ReviewAccessService> access;
    private final ReviewTicketRepository tickets;
    private final ReviewMapper mapper;
    private final ReviewDecisionRepository decisions;
    private final ReviewNotificationRepository notifications;
    private final AuditService audit;
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "sourceTicketId",
            "sourceTicketId",
            "title",
            "title",
            "project",
            "projectName",
            "authorName",
            "authorName",
            "enteredQueueAt",
            "enteredQueueAt",
            "reviewDueDate",
            "reviewDueDate");

    public ReviewServiceImpl(
            ObjectProvider<ReviewAccessService> access,
            ReviewTicketRepository tickets,
            ReviewMapper mapper,
            ReviewDecisionRepository decisions,
            ReviewNotificationRepository notifications,
            AuditService audit) {
        this.access = access;
        this.tickets = tickets;
        this.mapper = mapper;
        this.decisions = decisions;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewQueueItem> queue(Long projectId, Pageable pageable) {
        Set<Long> projects = managedProjects(currentActor(), projectId);
        return tickets.findByProjectIdInAndStatusAndSource(
                        projects, ReviewStatus.PENDING_REVIEW, ReviewSource.SOLUTION_FORM, queuePaging(pageable))
                .map(mapper::toQueueItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> queueTicketIds(Long projectId) {
        return tickets.findQueueIds(
                managedProjects(currentActor(), projectId), ReviewStatus.PENDING_REVIEW, ReviewSource.SOLUTION_FORM);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewTicketResponse get(long ticketId) {
        ReviewActor actor = currentActor();
        ReviewTicket ticket = find(ticketId);
        requireReadAccess(actor, ticket);
        return mapper.toResponse(ticket);
    }

    @Override
    @Transactional
    public ReviewDecisionResult decide(List<Long> ticketIds, ReviewAction action, String comment) {
        ReviewActor actor = currentActor();
        validateIds(ticketIds);
        if (action == null || (action == ReviewAction.REQUEST_CHANGES && ticketIds.size() != 1)) {
            throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG06");
        }
        List<ReviewTicket> selected = new ArrayList<>();
        // Stable lock order avoids deadlocks when overlapping batches arrive in a different UI order.
        for (Long id : ticketIds.stream().sorted().toList()) {
            ReviewTicket ticket =
                    tickets.lockById(id).orElseThrow(() -> new ReviewException(HttpStatus.NOT_FOUND, "MSG04"));
            if (!actor.managedProjects().containsKey(ticket.getProjectId())) {
                throw new ReviewException(HttpStatus.FORBIDDEN, "MSG08");
            }
            ticket.validateDecision(action, comment);
            selected.add(ticket);
        }
        Instant at = Instant.now();
        for (ReviewTicket ticket : selected) {
            ticket.decide(action, comment, at);
            ReviewDecision decision = decisions.save(new ReviewDecision(ticket, actor, action, comment, at));
            audit.record(actor.id(), action.name(), "ticket:" + ticket.getId(), at);
            if (action != ReviewAction.APPROVE) notifications.save(new ReviewNotification(decision));
        }
        return new ReviewDecisionResult(selected.size());
    }

    @Override
    @Transactional
    public ReviewTicketResponse registerSubmission(ReviewRegistration submission) {
        // Internal submission port only. The integrating module verifies submitter membership and dates.
        return mapper.toResponse(tickets.save(new ReviewTicket(submission)));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewTicketResponse getPublished(long ticketId) {
        ReviewActor actor = currentActor();
        ReviewTicket ticket = find(ticketId);
        requireReadAccess(actor, ticket);
        if (ticket.getStatus() != ReviewStatus.PUBLISHED) throw new ReviewException(HttpStatus.NOT_FOUND, "MSG04");
        return mapper.toResponse(ticket);
    }

    private ReviewActor currentActor() {
        ReviewAccessService adapter = access.getIfAvailable();
        if (adapter == null) throw new ReviewException(HttpStatus.FORBIDDEN, "MSG08");
        ReviewActor actor = adapter.currentActor();
        if (actor == null) throw new ReviewException(HttpStatus.UNAUTHORIZED, "MSG07");
        return actor;
    }

    private Set<Long> managedProjects(ReviewActor actor, Long projectId) {
        if (projectId != null && projectId <= 0) throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG06");
        if (actor.managedProjects().isEmpty()
                || (projectId != null && !actor.managedProjects().containsKey(projectId))) {
            throw new ReviewException(HttpStatus.FORBIDDEN, "MSG08");
        }
        return projectId == null ? actor.managedProjects().keySet() : Set.of(projectId);
    }

    private ReviewTicket find(long id) {
        if (id <= 0) throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG06");
        return tickets.findById(id).orElseThrow(() -> new ReviewException(HttpStatus.NOT_FOUND, "MSG04"));
    }

    private void requireReadAccess(ReviewActor actor, ReviewTicket ticket) {
        if (!actor.managedProjects().containsKey(ticket.getProjectId())
                && !actor.readableProjects().contains(ticket.getProjectId())) {
            throw new ReviewException(HttpStatus.FORBIDDEN, "MSG08");
        }
    }

    private void validateIds(List<Long> ids) {
        if (ids == null
                || ids.isEmpty()
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG06");
        }
    }

    private Pageable queuePaging(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG06");
        }
        Sort sort = pageable.getSort().isUnsorted() ? Sort.by("reviewDueDate") : pageable.getSort();
        List<Sort.Order> orders = sort.stream()
                .map(order -> {
                    String field = SORT_FIELDS.get(order.getProperty());
                    if (field == null) throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG06");
                    return new Sort.Order(order.getDirection(), field);
                })
                .toList();
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(orders).and(Sort.by("id")));
    }
}
