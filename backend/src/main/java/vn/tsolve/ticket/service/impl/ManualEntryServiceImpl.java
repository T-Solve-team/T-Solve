package vn.tsolve.ticket.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ManualEntryRequest;
import vn.tsolve.ticket.dto.ManualEntryResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketEvidence;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.exception.ImportFailedException;
import vn.tsolve.ticket.exception.ManualEntryRejectedException;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ManualEntryService;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * Manual entry (UC-22). The Ticket, its approve decision, the Completed Import Batch, the "tickets published" event
 * and the audit record share one transaction; when it fails, the Failed Import Batch is written afterwards in a
 * transaction of its own, as Confirm does.
 */
@Service
public class ManualEntryServiceImpl implements ManualEntryService {

    private static final Logger log = LoggerFactory.getLogger(ManualEntryServiceImpl.class);
    /** The unique constraint on the Ticket ID (column source_ticket_id). */
    private static final String TICKET_ID_CONSTRAINT = "uq_ticket_ticket_id";

    private final ObjectMapper json;
    private final ManualEntryValidator validator;
    private final EvidenceFiles evidence;
    private final ProjectAccess projects;
    private final TicketRepository tickets;
    private final TicketEvidenceRepository evidenceRows;
    private final TicketReviewDecisionRepository decisions;
    private final ImportBatchRepository batches;
    private final ImportBatchRecorder recorder;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final AuditService audit;

    public ManualEntryServiceImpl(
            ObjectMapper json,
            ManualEntryValidator validator,
            EvidenceFiles evidence,
            ProjectAccess projects,
            TicketRepository tickets,
            TicketEvidenceRepository evidenceRows,
            TicketReviewDecisionRepository decisions,
            ImportBatchRepository batches,
            ImportBatchRecorder recorder,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            AuditService audit) {
        this.json = json;
        this.validator = validator;
        this.evidence = evidence;
        this.projects = projects;
        this.tickets = tickets;
        this.evidenceRows = evidenceRows;
        this.decisions = decisions;
        this.batches = batches;
        this.recorder = recorder;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.audit = audit;
    }

    @Override
    public ManualEntryResponse save(TSolveUser user, String entry, List<MultipartFile> files) {
        // order (design Save): field checks, then Project access, then the Ticket ID — a rejected entry saves
        // nothing and records no Import Batch
        ManualEntryValidator.Entry valid = validator.validate(read(entry));
        String caller = user.getName();
        if (!projects.managesActive(caller, valid.projectId())) {
            throw new ProjectAccessDeniedException();
        }
        evidence.check(files);
        if (!tickets.findExistingTicketIds(List.of(valid.ticketId())).isEmpty()) {
            throw ManualEntryRejectedException.ticketIdTaken(valid.ticketId());
        }

        // stored before the transaction, so no object-storage call holds it open (backend/AGENTS.md §4)
        List<EvidenceFiles.Stored> stored;
        try {
            stored = evidence.store(files);
        } catch (RuntimeException e) {
            fail(valid.projectId(), caller, e);
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        UUID ticketId;
        try {
            ticketId = transaction.execute(status -> insert(user, valid, stored));
        } catch (RuntimeException e) {
            evidence.delete(stored);
            if (e instanceof DataIntegrityViolationException && namesTicketIdConstraint(e)) {
                // another request saved this Ticket ID after the check above (AC-006.8): not a failure, no batch
                throw ManualEntryRejectedException.ticketIdTaken(valid.ticketId());
            }
            fail(valid.projectId(), caller, e);
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ManualEntryResponse(ticketId, new MessageRef("MSG32", Map.of("created", 1, "skipped", 0)));
    }

    /** The part is read as text, so it works whatever content type the browser gives it (design: entry part). */
    private ManualEntryRequest read(String entry) {
        try {
            ManualEntryRequest request = json.readValue(entry, ManualEntryRequest.class);
            if (request == null) {
                throw ManualEntryRejectedException.unreadable();
            }
            return request;
        } catch (JsonProcessingException e) {
            throw ManualEntryRejectedException.unreadable();
        }
    }

    private UUID insert(TSolveUser user, ManualEntryValidator.Entry entry, List<EvidenceFiles.Stored> stored) {
        String caller = user.getName();
        ImportBatch batch =
                batches.save(ImportBatch.completed(entry.projectId(), caller, TicketSource.MANUAL, null, 1, 0, 0));
        UUID ticketId = tickets.save(Ticket.enteredManually(
                        entry.ticketId(),
                        entry.projectId(),
                        batch.getId(),
                        entry.title(),
                        entry.description(),
                        user.fullName(),
                        user.id(),
                        entry.resolvedDate(),
                        entry.solution()))
                .getId();
        if (!stored.isEmpty()) {
            evidenceRows.saveAll(stored.stream()
                    .map(file ->
                            TicketEvidence.of(ticketId, file.key(), file.fileName(), file.fileType(), file.fileSize()))
                    .toList());
        }
        decisions.save(TicketReviewDecision.approve(ticketId, caller));
        // inside the transaction: @TransactionalEventListener(AFTER_COMMIT) listeners get it once this commits
        events.publishEvent(new TicketsPublishedEvent(List.of(ticketId)));
        audit.record(
                AuditAction.IMPORT, "PROJECT", entry.projectId(), Map.of("importBatchId", batch.getId(), "created", 1));
        return ticketId;
    }

    private static boolean namesTicketIdConstraint(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(TICKET_ID_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    /** Records the Failed batch; never lets a recording problem hide the MSG33 answer. */
    private void fail(UUID projectId, String caller, RuntimeException cause) {
        // the class only: the message of a database error can hold ticket content (backend/AGENTS.md §9)
        String reason = cause.getClass().getSimpleName();
        log.warn("Manual entry for project {} failed: {}", projectId, reason);
        try {
            recorder.recordFailedWithoutPreview(projectId, caller, TicketSource.MANUAL, reason + ": MSG33");
        } catch (RuntimeException e) {
            log.error(
                    "Could not record the Failed Import Batch of a manual entry for project {}: {}",
                    projectId,
                    e.getClass().getSimpleName());
        }
    }
}
