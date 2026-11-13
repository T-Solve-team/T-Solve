package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.InvalidFieldsException;
import vn.tsolve.common.exception.ProblemDetails.FieldError;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.csv.CsvRowValidator;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.ManualTicketRequest;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ManualTicketService;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * Manual entry (UC-22). Fields are checked first, all at once (AC-006.6), then the Project (403); the Ticket, its
 * approve decision and its Completed Import Batch are written in one transaction. A Ticket ID taken meanwhile stops
 * the insert at the unique index and rolls everything back; manual entry records no Failed batch.
 */
@Service
public class ManualTicketServiceImpl implements ManualTicketService {

    private static final int TICKET_ID_MAX = 50;
    private static final int TITLE_MAX = 200;
    private static final int DESCRIPTION_MAX = 10_000;

    private final TicketRepository tickets;
    private final TicketReviewDecisionRepository decisions;
    private final ImportBatchRepository batches;
    private final ProjectAccess projects;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    /** Its zone decides what "today" is for the Resolved date (Vietnam time). */
    private final Clock clock;

    public ManualTicketServiceImpl(
            TicketRepository tickets,
            TicketReviewDecisionRepository decisions,
            ImportBatchRepository batches,
            ProjectAccess projects,
            ApplicationEventPublisher events,
            AuditService audit,
            Clock clock) {
        this.tickets = tickets;
        this.decisions = decisions;
        this.batches = batches;
        this.projects = projects;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ImportResultResponse create(TSolveUser user, ManualTicketRequest request) {
        List<FieldError> errors = validate(request);
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        String owner = user.getName();
        if (!projects.managesActive(owner, request.projectId())) {
            throw new ProjectAccessDeniedException();
        }
        String ticketId = CsvRowValidator.trim(request.ticketId());
        if (!tickets.findExistingTicketIds(List.of(ticketId)).isEmpty()) {
            throw ticketIdTaken(ticketId);
        }

        ImportBatch batch =
                batches.save(ImportBatch.completed(request.projectId(), owner, TicketSource.MANUAL, null, 1, 0, 0));
        String resolved = CsvRowValidator.trim(request.resolvedDate());
        Ticket ticket;
        try {
            // flushed here, so a Ticket ID another request took meanwhile fails inside this method (AC-006.7)
            ticket = tickets.saveAndFlush(Ticket.manual(
                    ticketId,
                    request.projectId(),
                    batch.getId(),
                    CsvRowValidator.trim(request.title()),
                    CsvRowValidator.trim(request.description()),
                    ImportConfirmServiceImpl.cut(user.fullName(), ImportConfirmServiceImpl.WRITER_MAX),
                    user.id(),
                    resolved == null ? null : LocalDate.parse(resolved),
                    new Ticket.Solution(
                            CsvRowValidator.trim(request.symptoms()),
                            CsvRowValidator.trim(request.rootCause()),
                            CsvRowValidator.trim(request.resolutionSteps()),
                            CsvRowValidator.trim(request.workaround()),
                            CsvRowValidator.trim(request.scope()))));
        } catch (DataIntegrityViolationException e) {
            if (ImportConfirmServiceImpl.UNIQUE_VIOLATION.equals(ImportConfirmServiceImpl.sqlState(e))) {
                throw ticketIdTaken(ticketId);
            }
            throw e;
        }
        decisions.save(TicketReviewDecision.approve(ticket.getId(), owner));
        // inside the transaction: @TransactionalEventListener(AFTER_COMMIT) listeners get it once this commits
        events.publishEvent(new TicketsPublishedEvent(List.of(ticket.getId())));
        audit.record(
                AuditAction.IMPORT,
                "PROJECT",
                request.projectId(),
                Map.of("importBatchId", batch.getId(), "created", 1));
        return new ImportResultResponse(
                batch.getId(), 1, 0, new MessageRef("MSG32", Map.of("created", 1, "skipped", 0)), null);
    }

    private static BusinessRuleException ticketIdTaken(String ticketId) {
        return new BusinessRuleException("MSG31", Map.of("ticket_id", ticketId), "ticketId");
    }

    /** One error per field, in the order of the form; blank counts as missing, lengths count code points. */
    private List<FieldError> validate(ManualTicketRequest r) {
        List<FieldError> errors = new ArrayList<>();
        if (r.projectId() == null) {
            errors.add(required("projectId"));
        }
        text(errors, "ticketId", r.ticketId(), TICKET_ID_MAX);
        text(errors, "title", r.title(), TITLE_MAX);
        text(errors, "description", r.description(), DESCRIPTION_MAX);
        String resolved = CsvRowValidator.trim(r.resolvedDate());
        if (resolved != null && !isPastOrToday(resolved)) {
            errors.add(new FieldError("resolvedDate", "MSG39", Map.of()));
        }
        text(errors, "symptoms", r.symptoms(), null);
        text(errors, "rootCause", r.rootCause(), null);
        text(errors, "resolutionSteps", r.resolutionSteps(), null);
        text(errors, "scope", r.scope(), null);
        int solution = Stream.of(r.symptoms(), r.rootCause(), r.resolutionSteps(), r.workaround(), r.scope())
                .map(CsvRowValidator::trim)
                .mapToInt(s -> s == null ? 0 : length(s))
                .sum();
        if (solution > CsvRowValidator.SOLUTION_MAX_LENGTH) {
            errors.add(new FieldError(
                    "solution",
                    "MSG02",
                    Map.of("field_name", "Solution", "max_length", CsvRowValidator.SOLUTION_MAX_LENGTH)));
        }
        return errors;
    }

    private static void text(List<FieldError> errors, String field, String value, Integer max) {
        String trimmed = CsvRowValidator.trim(value);
        if (trimmed == null) {
            errors.add(required(field));
        } else if (max != null && length(trimmed) > max) {
            errors.add(new FieldError(field, "MSG02", Map.of("field_name", field, "max_length", max)));
        }
    }

    private static FieldError required(String field) {
        return new FieldError(field, "MSG01", Map.of("field_name", field));
    }

    private boolean isPastOrToday(String date) {
        try {
            return !LocalDate.parse(date).isAfter(LocalDate.now(clock));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
