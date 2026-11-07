package vn.tsolve.ticket.service.impl;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.TicketReviewView.Detail;
import vn.tsolve.ticket.dto.TicketReviewView.Evidence;
import vn.tsolve.ticket.dto.TicketReviewView.Feedback;
import vn.tsolve.ticket.dto.TicketReviewView.HistoryFilter;
import vn.tsolve.ticket.dto.TicketReviewView.HistoryItem;
import vn.tsolve.ticket.dto.TicketReviewView.ProjectAccess;
import vn.tsolve.ticket.dto.TicketReviewView.QueueItem;
import vn.tsolve.ticket.dto.TicketReviewView.QueueRow;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.exception.InvalidReviewHistoryFilterException;
import vn.tsolve.ticket.mapper.TicketReviewMapper;
import vn.tsolve.ticket.repository.ReviewDecisionRecordRepository;
import vn.tsolve.ticket.repository.ReviewDecisionRecordRepository.HistoryRow;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.service.TicketReviewReadService;

@Service
@Transactional(readOnly = true)
public class TicketReviewReadServiceImpl implements TicketReviewReadService {

    private static final ZoneId REVIEW_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String INVALID_DATE_RANGE = "MSG39";
    private static final String INVALID_FILTER = "MSG06";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern CALENDAR_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter STRICT_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final Instant NO_LOWER_BOUND = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant NO_UPPER_BOUND = Instant.parse("9999-01-01T00:00:00Z");

    /** Contract decision value → stored review_decision.decision. Other contract values have no stored rows. */
    private static final Map<String, String> STORED_DECISION =
            Map.of("APPROVE", "APPROVED", "REJECT", "REJECTED", "REQUEST_CHANGES", "CHANGES_REQUESTED");

    private static final List<String> HISTORY_ONLY_DECISIONS = List.of("UNPUBLISH", "REPUBLISH", "SEND_BACK");

    private final TicketRepository tickets;
    private final TicketEvidenceRepository evidence;
    private final ReviewDecisionRecordRepository decisions;
    private final TicketReviewMapper mapper;

    public TicketReviewReadServiceImpl(
            TicketRepository tickets,
            TicketEvidenceRepository evidence,
            ReviewDecisionRecordRepository decisions,
            TicketReviewMapper mapper) {
        this.tickets = tickets;
        this.evidence = evidence;
        this.decisions = decisions;
        this.mapper = mapper;
    }

    @Override
    public Page<HistoryItem> history(Map<UUID, ProjectAccess> projects, HistoryFilter filter) {
        Range range = historyRange(filter.dateFrom(), filter.dateTo());
        if (filter.page() < 0 || filter.size() < 1 || filter.size() > MAX_PAGE_SIZE) {
            throw new InvalidReviewHistoryFilterException(INVALID_FILTER);
        }
        boolean newestFirst = newestFirst(filter.sort());
        List<String> stored = storedDecisions(filter.decision());
        Pageable page = PageRequest.of(filter.page(), filter.size());
        List<UUID> managed = projects.entrySet().stream()
                .filter(project -> project.getValue().managed())
                .filter(project -> filter.departmentId() == null
                        || filter.departmentId().equals(project.getValue().departmentId()))
                .map(Map.Entry::getKey)
                .toList();
        if (managed.isEmpty() || stored.isEmpty()) {
            return Page.empty(page);
        }
        Page<HistoryRow> rows = newestFirst
                ? decisions.findHistoryNewestFirst(managed, stored, range.from(), range.to(), page)
                : decisions.findHistoryOldestFirst(managed, stored, range.from(), range.to(), page);
        return rows.map(row -> historyItem(row, projects));
    }

    @Override
    public Page<QueueItem> queue(
            Map<UUID, ProjectAccess> projects, UUID departmentId, Pageable page, List<String> sort) {
        Comparator<QueueItem> order = queueOrder(sort);
        // ponytail: sorts the managed queue in memory; use database paging when queue volume requires it.
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
                .sorted(order)
                .skip(page.getOffset())
                .limit(page.getPageSize())
                .toList();
        return new PageImpl<>(items, page, rows.size());
    }

    @Override
    public List<UUID> eligibleIds(Map<UUID, ProjectAccess> projects, UUID departmentId) {
        return queueRows(projects, departmentId).stream()
                .map(QueueRow::ticketId)
                .sorted()
                .toList();
    }

    @Override
    public Detail detail(Map<UUID, ProjectAccess> projects, UUID accountId, boolean manager, UUID ticketId) {
        var ticket = tickets.findForReviewDetail(ticketId).orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
        var project = projects.get(ticket.getProjectId());
        boolean canReadReview =
                Objects.equals(ticket.getWriterId(), accountId) || (manager && project != null && project.managed());
        if (project == null || (ticket.getStatus() != TicketStatus.PUBLISHED && !canReadReview)) {
            throw new NotFoundException("MSG04", Map.of());
        }
        List<Evidence> attachments = evidence.findByTicketIdOrderById(ticket.getId()).stream()
                .map(mapper::evidence)
                .toList();
        return mapper.detail(
                ticket,
                project.project(),
                dueAt(ticket.getReviewDueDate()),
                attachments,
                canReadReview
                        ? (ticket.getStatus() == TicketStatus.REJECTED
                                ? ticket.getRejectionReason()
                                : ticket.getChangeRequestComment())
                        : null,
                manager
                        && project.managed()
                        && ticket.getSource() == TicketSource.SOLUTION_FORM
                        && ticket.getStatus() == TicketStatus.PENDING_REVIEW);
    }

    @Override
    public Optional<Feedback> jiraFeedback(UUID projectId, UUID writerId, String issueKey) {
        return tickets.findJiraFeedback(projectId, writerId, issueKey, PageRequest.of(0, 1)).stream()
                .findFirst();
    }

    private List<QueueRow> queueRows(Map<UUID, ProjectAccess> projects, UUID departmentId) {
        List<UUID> managed = projects.entrySet().stream()
                .filter(project -> project.getValue().managed())
                .filter(project -> departmentId == null
                        || departmentId.equals(project.getValue().departmentId()))
                .map(Map.Entry::getKey)
                .toList();
        return managed.isEmpty() ? List.of() : tickets.findReviewQueue(managed);
    }

    private static HistoryItem historyItem(HistoryRow row, Map<UUID, ProjectAccess> projects) {
        String decision =
                switch (row.getDecision()) {
                    case "APPROVED" -> "APPROVE";
                    case "REJECTED" -> "REJECT";
                    case "CHANGES_REQUESTED" -> "REQUEST_CHANGES";
                    default -> throw new IllegalStateException("Unknown stored review decision");
                };
        return new HistoryItem(
                row.getDecisionId(),
                row.getTicketId(),
                row.getSourceTicketId(),
                row.getTitle(),
                projects.get(row.getProjectId()).project(),
                decision,
                row.getActorId(),
                row.getActorName(),
                row.getAuthorName(),
                row.getDecidedAt(),
                "REJECT".equals(decision) ? row.getFeedback() : null,
                "REQUEST_CHANGES".equals(decision) ? row.getFeedback() : null);
    }

    private static List<String> storedDecisions(String decision) {
        if (decision == null) {
            return List.copyOf(STORED_DECISION.values());
        }
        if (HISTORY_ONLY_DECISIONS.contains(decision)) {
            return List.of();
        }
        String stored = STORED_DECISION.get(decision);
        if (stored == null) {
            throw new InvalidReviewHistoryFilterException(INVALID_FILTER);
        }
        return List.of(stored);
    }

    private static boolean newestFirst(List<String> sort) {
        if (sort == null || sort.isEmpty()) {
            return true;
        }
        if (sort.size() == 1) {
            switch (sort.get(0)) {
                case "timestamp,desc":
                    return true;
                case "timestamp", "timestamp,asc":
                    return false;
                default:
                    break;
            }
        }
        throw new InvalidReviewHistoryFilterException(INVALID_FILTER);
    }

    private static Range historyRange(String from, String to) {
        if (from == null && to == null) {
            return new Range(NO_LOWER_BOUND, NO_UPPER_BOUND);
        }
        if (from == null
                || to == null
                || !CALENDAR_DATE.matcher(from).matches()
                || !CALENDAR_DATE.matcher(to).matches()) {
            throw new InvalidReviewHistoryFilterException(INVALID_DATE_RANGE);
        }
        LocalDate first;
        LocalDate last;
        try {
            first = LocalDate.parse(from, STRICT_DATE);
            last = LocalDate.parse(to, STRICT_DATE);
        } catch (DateTimeException invalid) {
            throw new InvalidReviewHistoryFilterException(INVALID_DATE_RANGE);
        }
        if (first.isAfter(last)) {
            throw new InvalidReviewHistoryFilterException(INVALID_DATE_RANGE);
        }
        return new Range(
                first.atStartOfDay(REVIEW_ZONE).toInstant(),
                last.plusDays(1).atStartOfDay(REVIEW_ZONE).toInstant());
    }

    /** Calendar dates in Vietnam time as [from, to). */
    private record Range(Instant from, Instant to) {}

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
        if (parts.length == 2 && !"asc".equalsIgnoreCase(parts[1]) && !"desc".equalsIgnoreCase(parts[1])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        boolean descending = parts.length == 2 && "desc".equalsIgnoreCase(parts[1]);
        return switch (parts[0]) {
            case "sourceTicketId" -> compare(QueueItem::sourceTicketId, descending);
            case "title" -> compare(QueueItem::title, descending);
            case "project" -> compare(item -> item.project().name(), descending);
            case "authorName" -> compare(QueueItem::authorName, descending);
            case "enteredQueueAt" -> compare(QueueItem::enteredQueueAt, descending);
            case "reviewDueDate" -> compare(QueueItem::reviewDueDate, descending);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
    }

    private static <T extends Comparable<? super T>> Comparator<QueueItem> compare(
            Function<QueueItem, T> key, boolean descending) {
        Comparator<T> order = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(key, Comparator.nullsLast(order));
    }
}
