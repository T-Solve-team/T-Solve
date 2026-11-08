package vn.tsolve.ticket.service.impl;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.csv.CsvRowValidator;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.ImportType;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.exception.ImportFailedException;
import vn.tsolve.ticket.exception.NothingToImportException;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.exception.ProjectNotActiveException;
import vn.tsolve.ticket.exception.RowsNotReadyException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ImportConfirmService;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * Confirm (UC-21 steps 7–9) and Finish of a Jira preview (UC-20). The import runs in one transaction that locks the
 * preview; when it fails, the Failed Import Batch is written afterwards in a transaction of its own. The "tickets
 * published" event is published inside the import transaction for {@code @TransactionalEventListener(AFTER_COMMIT)}
 * listeners.
 */
@Service
public class ImportConfirmServiceImpl implements ImportConfirmService {

    private static final Logger log = LoggerFactory.getLogger(ImportConfirmServiceImpl.class);
    private static final int TITLE_MAX = 200;
    private static final int WRITER_MAX = 100;
    private static final String UNIQUE_VIOLATION = "23505";

    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final TicketRepository tickets;
    private final TicketReviewDecisionRepository decisions;
    private final ImportBatchRepository batches;
    private final ImportBatchRecorder recorder;
    private final ProjectAccess projects;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final AuditService audit;

    public ImportConfirmServiceImpl(
            ImportPreviewRepository previews,
            ImportPreviewRowRepository previewRows,
            TicketRepository tickets,
            TicketReviewDecisionRepository decisions,
            ImportBatchRepository batches,
            ImportBatchRecorder recorder,
            ProjectAccess projects,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            AuditService audit) {
        this.previews = previews;
        this.previewRows = previewRows;
        this.tickets = tickets;
        this.decisions = decisions;
        this.batches = batches;
        this.recorder = recorder;
        this.projects = projects;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.audit = audit;
    }

    /** A Ticket ID became an existing Ticket after the preview was made (AC-004.7). */
    private static class TicketIdTakenException extends RuntimeException {}

    /** skippedKeys: Jira issues skipped because they already had a Ticket (MSG38); always empty for CSV. */
    private record Imported(long batchId, int created, long skipped, List<String> skippedKeys) {}

    @Override
    public ImportResultResponse confirm(String user, long previewId) {
        ImportPreview preview = previews.findByIdAndOwner(previewId, user)
                .orElseThrow(() -> new NotFoundException("Import preview " + previewId));
        long errorRows = previewRows.countByIdPreviewIdAndStatus(previewId, PreviewRowStatus.ERROR);
        long removedRows = previewRows.countByIdPreviewIdAndStatus(previewId, PreviewRowStatus.REMOVED);
        // first, so a file with only Needs Solution rows answers MSG_NEW_4, not MSG30 (design, edge case 47)
        requireRowsReady(preview, errorRows);
        requireRowsToImport(
                preview,
                previewRows
                        .findByIdPreviewIdAndStatus(previewId, PreviewRowStatus.VALID)
                        .isEmpty(),
                errorRows);
        // rejected before anything starts, so no Import Batch is recorded (design B1, E1): MSG40 first, then 403
        if (!projects.isActive(preview.getProjectId())) {
            throw new ProjectNotActiveException();
        }
        if (!projects.managesActive(user, preview.getProjectId())) {
            throw new ProjectAccessDeniedException();
        }

        Imported imported;
        try {
            imported = importOrRetry(user, preview);
        } catch (NotFoundException | NothingToImportException | RowsNotReadyException e) {
            // another Confirm or remove-rows got there first: nothing was attempted (AC-004.12)
            throw e;
        } catch (TicketIdTakenException e) {
            fail(preview, errorRows, removedRows, "TicketIdTaken", e);
            throw new ImportFailedException(HttpStatus.CONFLICT);
        } catch (DataIntegrityViolationException e) {
            // only a unique violation is a taken Ticket ID; a value too long or a NOT NULL names itself (BUG-190)
            boolean taken = UNIQUE_VIOLATION.equals(sqlState(e));
            fail(preview, errorRows, removedRows, taken ? "TicketIdTaken" : e.getClass().getSimpleName(), e);
            throw new ImportFailedException(taken ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RuntimeException e) {
            fail(preview, errorRows, removedRows, e.getClass().getSimpleName(), e);
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        List<String> skippedKeys = imported.skippedKeys();
        return new ImportResultResponse(
                imported.batchId(),
                imported.created(),
                imported.skipped(),
                new MessageRef("MSG32", Map.of("created", imported.created(), "skipped", imported.skipped())),
                skippedKeys.isEmpty()
                        ? null
                        : new MessageRef(
                                "MSG38",
                                Map.of("count", skippedKeys.size(), "issue_keys", String.join(", ", skippedKeys))));
    }

    /**
     * A Jira preview retries once when the unique Ticket ID stops the insert: a Ticket with one of its keys was made
     * after the keys were read (design Edge 6). The retry reads the taken keys again and skips them (AC-003.2).
     */
    private Imported importOrRetry(String user, ImportPreview preview) {
        try {
            return transaction.execute(status -> importRows(user, preview.getId()));
        } catch (DataIntegrityViolationException e) {
            if (!isJira(preview)) {
                throw e;
            }
            log.info("Finish of Jira preview {} hit a taken Ticket ID; retrying once", preview.getId());
            return transaction.execute(status -> importRows(user, preview.getId()));
        }
    }

    private static boolean isJira(ImportPreview preview) {
        return preview.getSource() == TicketSource.JIRA_IMPORT;
    }

    /**
     * CSV: at least one Valid row (MSG30). Jira: no row still Needs Solution and at least one Valid row (MSG_NEW_4,
     * AC-003.6).
     */
    private void requireRowsToImport(ImportPreview preview, boolean noValidRow, long errorRows) {
        if (isJira(preview)) {
            long needsSolution =
                    previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.NEEDS_SOLUTION);
            if (needsSolution > 0 || noValidRow) {
                throw new RowsNotReadyException(needsSolution);
            }
        } else if (noValidRow) {
            throw new NothingToImportException(errorRows);
        }
    }

    /** A file without Solutions is imported only when no row still needs a Solution or has errors (AC-014.1). */
    private void requireRowsReady(ImportPreview preview, long errorRows) {
        if (preview.getImportType() != ImportType.WITHOUT_SOLUTION) {
            return;
        }
        long notReady =
                previewRows.countByIdPreviewIdAndStatus(preview.getId(), PreviewRowStatus.NEEDS_SOLUTION) + errorRows;
        if (notReady > 0) {
            throw new RowsNotReadyException(notReady);
        }
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
        // again under the lock: Remove and Fill solution do not wait for the first check (edge case 48)
        requireRowsReady(preview, errorRows);
        List<ImportPreviewRow> valid = previewRows.findByIdPreviewIdAndStatus(previewId, PreviewRowStatus.VALID);
        requireRowsToImport(preview, valid.isEmpty(), errorRows);

        List<String> ids = valid.stream()
                .map(r -> CsvRowValidator.trim(r.getValues().ticketId()))
                .toList();
        Set<String> taken = tickets.findExistingTicketIds(ids);
        if (!taken.isEmpty() && !isJira(preview)) {
            throw new TicketIdTakenException();
        }
        // Jira: an issue that became a Ticket since the pull is skipped, not an error (AC-003.2, AC-003.3)
        List<ImportPreviewRow> toImport = valid.stream()
                .filter(r -> !taken.contains(CsvRowValidator.trim(r.getValues().ticketId())))
                .toList();
        List<String> skippedKeys =
                ids.stream().filter(taken::contains).distinct().toList();
        long skipped = isJira(preview) ? skippedKeys.size() : errorRows + removedRows;

        ImportBatch batch = batches.save(ImportBatch.completed(
                preview.getProjectId(),
                user,
                preview.getSource(),
                preview.getFileName(),
                toImport.size(),
                (int) (isJira(preview) ? skipped : errorRows),
                (int) removedRows));
        List<Ticket> saved = tickets.saveAll(toImport.stream()
                .map(r -> toTicket(r.getValues(), preview, batch.getId()))
                .toList());
        List<UUID> ticketIds = saved.stream().map(Ticket::getId).toList();
        decisions.saveAll(ticketIds.stream()
                .map(id -> TicketReviewDecision.approve(id, user))
                .toList());
        // a Draft is the preview itself, so this also deletes the Draft (AC-003.10)
        previews.deleteById(preview.getId());
        if (!ticketIds.isEmpty()) {
            // inside the transaction: @TransactionalEventListener(AFTER_COMMIT) listeners get it once this commits
            events.publishEvent(new TicketsPublishedEvent(ticketIds));
        }
        audit.record(
                AuditAction.IMPORT,
                "PROJECT",
                preview.getProjectId(),
                Map.of("importBatchId", batch.getId(), "created", toImport.size(), "skipped", skipped));
        return new Imported(batch.getId(), toImport.size(), skipped, skippedKeys);
    }

    /**
     * ticket.title is VARCHAR(200) and ticket.writer VARCHAR(100); a longer Jira summary or assignee is cut on the
     * Ticket only, never inside a character (BUG-190).
     */
    private static String cut(String text, int max) {
        if (text == null || text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max));
    }

    private static Ticket toTicket(ImportPreviewRow.Values v, ImportPreview preview, long batchId) {
        String resolved = CsvRowValidator.trim(v.resolvedDate());
        return Ticket.imported(
                CsvRowValidator.trim(v.ticketId()),
                preview.getProjectId(),
                preview.getSource(),
                batchId,
                cut(CsvRowValidator.trim(v.title()), TITLE_MAX),
                CsvRowValidator.trim(v.description()),
                cut(CsvRowValidator.trim(v.author()), WRITER_MAX),
                resolved == null ? null : LocalDate.parse(resolved),
                new Ticket.Solution(
                        CsvRowValidator.trim(v.symptoms()),
                        CsvRowValidator.trim(v.rootCause()),
                        CsvRowValidator.trim(v.resolutionSteps()),
                        CsvRowValidator.trim(v.workaround()),
                        CsvRowValidator.trim(v.scope())));
    }
}
