package vn.tsolve.ticket.service.impl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.TicketReviewView.Detail;
import vn.tsolve.ticket.dto.TicketReviewView.Evidence;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.dto.TicketReviewView.QueueItem;
import vn.tsolve.ticket.dto.TicketReviewView.QueueRow;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.mapper.TicketReviewMapper;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.TicketReviewReadService;

@Service
@Transactional(readOnly = true)
public class TicketReviewReadServiceImpl implements TicketReviewReadService {

    private static final ZoneId REVIEW_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TicketRepository tickets;
    private final TicketEvidenceRepository evidence;
    private final TicketReviewMapper mapper;

    public TicketReviewReadServiceImpl(
            TicketRepository tickets, TicketEvidenceRepository evidence, TicketReviewMapper mapper) {
        this.tickets = tickets;
        this.evidence = evidence;
        this.mapper = mapper;
    }

    @Override
    public Page<QueueItem> queue(
            Map<Long, ProjectAccess> projects, UUID departmentId, Pageable page, List<String> sort) {
        List<QueueRow> rows = queueRows(projects, departmentId);
        List<QueueItem> items = rows.stream()
                .map(row -> new QueueItem(
                        row.ticketId(),
                        row.sourceTicketId(),
                        row.title(),
                        projects.get(row.projectId()).project(),
                        TicketSource.SOLUTION_FORM.name(),
                        row.authorName(),
                        row.enteredQueueAt(),
                        dueAt(row.reviewDueDate())))
                .sorted(queueOrder(sort))
                .skip(page.getOffset())
                .limit(page.getPageSize())
                .toList();
        return new PageImpl<>(items, page, rows.size());
    }

    @Override
    public List<UUID> eligibleIds(Map<Long, ProjectAccess> projects, UUID departmentId) {
        return queueRows(projects, departmentId).stream()
                .map(QueueRow::ticketId)
                .sorted()
                .toList();
    }

    @Override
    public Detail detail(Map<Long, ProjectAccess> projects, UUID accountId, boolean manager, UUID ticketId) {
        var ticket = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        var project = projects.get(ticket.getProjectId());
        boolean canReadReview =
                Objects.equals(ticket.getWriterId(), accountId) || (manager && project != null && project.managed());
        if (project == null || (ticket.getStatus() != TicketStatus.PUBLISHED && !canReadReview)) {
            throw new NotFoundException("MSG04", Map.of());
        }
        List<Evidence> attachments = evidence.findByTicketIdOrderById(ticketId).stream()
                .map(mapper::evidence)
                .toList();
        return mapper.detail(
                ticket,
                project.project(),
                dueAt(ticket.getReviewDueDate()),
                attachments,
                canReadReview ? ticket.getChangeRequestComment() : null);
    }

    private List<QueueRow> queueRows(Map<Long, ProjectAccess> projects, UUID departmentId) {
        List<Long> managed = projects.entrySet().stream()
                .filter(project -> project.getValue().managed())
                .filter(project -> departmentId == null
                        || departmentId.equals(project.getValue().departmentId()))
                .map(Map.Entry::getKey)
                .toList();
        return managed.isEmpty() ? List.of() : tickets.findReviewQueue(managed);
    }

    private static OffsetDateTime dueAt(LocalDate dueDate) {
        return dueDate == null ? null : dueDate.atStartOfDay(REVIEW_ZONE).toOffsetDateTime();
    }

    private static Comparator<QueueItem> queueOrder(List<String> sort) {
        return sort.stream()
                .map(TicketReviewReadServiceImpl::sortBy)
                .reduce(Comparator::thenComparing)
                .orElseGet(() -> compare(QueueItem::reviewDueDate, false))
                .thenComparing(QueueItem::ticketId);
    }

    private static Comparator<QueueItem> sortBy(String sort) {
        String[] parts = sort.split(",", 2);
        boolean descending = parts.length == 2 && "desc".equalsIgnoreCase(parts[1]);
        return switch (parts[0]) {
            case "sourceTicketId" -> compare(QueueItem::sourceTicketId, descending);
            case "title" -> compare(QueueItem::title, descending);
            case "project" -> compare(item -> item.project().name(), descending);
            case "authorName" -> compare(QueueItem::authorName, descending);
            case "enteredQueueAt" -> compare(QueueItem::enteredQueueAt, descending);
            case "reviewDueDate" -> compare(QueueItem::reviewDueDate, descending);
            default -> compare(QueueItem::reviewDueDate, false);
        };
    }

    private static <T extends Comparable<? super T>> Comparator<QueueItem> compare(
            Function<QueueItem, T> key, boolean descending) {
        Comparator<T> order = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(key, Comparator.nullsLast(order));
    }
}
