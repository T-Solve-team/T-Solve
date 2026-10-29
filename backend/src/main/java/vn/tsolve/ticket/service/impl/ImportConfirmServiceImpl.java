package vn.tsolve.ticket.service.impl;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.error.NotFoundException;
import vn.tsolve.ticket.csv.CsvRowValidator;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.exception.ImportFailedException;
import vn.tsolve.ticket.exception.NothingToImportException;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.ProjectNotActiveException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * Confirm (UC-21 steps 7–9). The import runs in one transaction that locks the preview; when it fails, the Failed
 * Import Batch is written afterwards in a transaction of its own. The "tickets published" event is published inside
 * the import transaction for {@code @TransactionalEventListener(AFTER_COMMIT)} listeners.
 */
@Service
public class ImportConfirmServiceImpl implements ImportConfirmService {

    private static final Logger log = LoggerFactory.getLogger(ImportConfirmServiceImpl.class);

    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final TicketRepository tickets;
    private final TicketReviewDecisionRepository decisions;
    private final ImportBatchRepository batches;
    private final ImportBatchRecorder recorder;
    private final ProjectAccess projects;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    public ImportConfirmServiceImpl(
            ImportPreviewRepository previews,
            ImportPreviewRowRepository previewRows,
            TicketRepository tickets,
            TicketReviewDecisionRepository decisions,
            ImportBatchRepository batches,
            ImportBatchRecorder recorder,
            ProjectAccess projects,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.previews = previews;
        this.previewRows = previewRows;
        this.tickets = tickets;
        this.decisions = decisions;
        this.batches = batches;
        this.recorder = recorder;
        this.projects = projects;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** A Ticket ID became an existing Ticket after the preview was made (AC-004.7). */
    private static class TicketIdTakenException extends RuntimeException {}

    private record Imported(long batchId, int created, long skipped) {}

    @Override
    public ImportResultResponse confirm(String user, long previewId) {
        ImportPreview preview = previews.findByIdAndOwner(previewId, user)
                .orElseThrow(() -> new NotFoundException("Import preview " + previewId));
        long errorRows = previewRows.countByIdPreviewIdAndStatus(previewId, PreviewRowStatus.ERROR);
        long removedRows = previewRows.countByIdPreviewIdAndStatus(previewId, PreviewRowStatus.REMOVED);
        if (previewRows
                .findByIdPreviewIdAndStatus(previewId, PreviewRowStatus.VALID)
                .isEmpty()) {
            throw new NothingToImportException(errorRows);
        }
        // rejected before anything starts, so no Import Batch is recorded (design B1, E1): MSG40 first, then 403
        if (!projects.isActive(preview.getProjectId())) {
            throw new ProjectNotActiveException();
        }
        if (!projects.managesActive(user, preview.getProjectId())) {
            throw new ProjectAccessDeniedException();
        }

        Imported imported;
        try {
            imported = transaction.execute(status -> importRows(user, previewId));
        } catch (NotFoundException | NothingToImportException e) {
            // another Confirm or remove-rows got there first: nothing was attempted (AC-004.12)
            throw e;
        } catch (TicketIdTakenException | DataIntegrityViolationException e) {
            fail(preview, errorRows, removedRows, "TicketIdTaken", e);
            throw new ImportFailedException(HttpStatus.CONFLICT);
        } catch (RuntimeException e) {
            fail(preview, errorRows, removedRows, e.getClass().getSimpleName(), e);
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ImportResultResponse(
                imported.batchId(),
                imported.created(),
                imported.skipped(),
                new MessageRef("MSG32", Map.of("created", imported.created(), "skipped", imported.skipped())));
    }

    /** Records the Failed batch; never lets a recording problem hide the MSG33 answer (design E5, E6). */
    private void fail(ImportPreview preview, long errorRows, long removedRows, String reason, RuntimeException cause) {
        log.warn(
                "Import of preview {} failed: {} (SQLState {})",
                preview.getId(),
                cause.getClass().getSimpleName(),
                sqlState(cause));
        try {
            recorder.recordFailed(preview, new ImportBatchRecorder.Counts(errorRows, removedRows), reason + ": MSG33");
        } catch (RuntimeException e) {
            log.error(
                    "Could not record the Failed Import Batch of preview {}: {}",
                    preview.getId(),
                    e.getClass().getSimpleName());
        }
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return "none";
    }

    private Imported importRows(String user, long previewId) {
        // the lock makes a second Confirm of this preview wait, then find it gone (AC-004.12)
        ImportPreview preview = previews.lockByIdAndOwner(previewId, user)
                .orElseThrow(() -> new NotFoundException("Import preview " + previewId));
        long errorRows = previewRows.countByIdPreviewIdAndStatus(previewId, PreviewRowStatus.ERROR);
        long removedRows = previewRows.countByIdPreviewIdAndStatus(previewId, PreviewRowStatus.REMOVED);
        List<ImportPreviewRow> valid = previewRows.findByIdPreviewIdAndStatus(previewId, PreviewRowStatus.VALID);
        if (valid.isEmpty()) {
            throw new NothingToImportException(errorRows);
        }

        List<String> ids = valid.stream()
                .map(r -> CsvRowValidator.trim(r.getValues().ticketId()))
                .toList();
        Set<String> taken = tickets.findExistingTicketIds(ids);
        if (!taken.isEmpty()) {
            throw new TicketIdTakenException();
        }

        ImportBatch batch = batches.save(ImportBatch.completed(
                preview.getProjectId(),
                user,
                preview.getSource(),
                preview.getFileName(),
                valid.size(),
                (int) errorRows,
                (int) removedRows));
        List<Ticket> saved = tickets.saveAll(valid.stream()
                .map(r -> toTicket(r.getValues(), preview, batch.getId()))
                .toList());
        List<Long> ticketIds = saved.stream().map(Ticket::getId).toList();
        decisions.saveAll(ticketIds.stream()
                .map(id -> TicketReviewDecision.approve(id, user))
                .toList());
        previews.deleteById(preview.getId());
        // inside the transaction: @TransactionalEventListener(AFTER_COMMIT) listeners get it once this commits
        events.publishEvent(new TicketsPublishedEvent(ticketIds));
        return new Imported(batch.getId(), valid.size(), errorRows + removedRows);
    }

    private static Ticket toTicket(ImportPreviewRow.Values v, ImportPreview preview, long batchId) {
        String resolved = CsvRowValidator.trim(v.resolvedDate());
        return Ticket.imported(
                CsvRowValidator.trim(v.ticketId()),
                preview.getProjectId(),
                preview.getSource(),
                batchId,
                CsvRowValidator.trim(v.title()),
                CsvRowValidator.trim(v.description()),
                CsvRowValidator.trim(v.author()),
                resolved == null ? null : LocalDate.parse(resolved),
                new Ticket.Solution(
                        CsvRowValidator.trim(v.symptoms()),
                        CsvRowValidator.trim(v.rootCause()),
                        CsvRowValidator.trim(v.resolutionSteps()),
                        CsvRowValidator.trim(v.workaround()),
                        CsvRowValidator.trim(v.scope())));
    }
}
