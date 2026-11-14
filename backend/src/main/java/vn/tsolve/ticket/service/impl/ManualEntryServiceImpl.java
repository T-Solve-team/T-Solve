package vn.tsolve.ticket.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
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
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftEvidenceResponse;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ManualDraftResponse;
import vn.tsolve.ticket.dto.ManualEntryRequest;
import vn.tsolve.ticket.dto.ManualEntryResponse;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.DraftEvidence;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketEvidence;
import vn.tsolve.ticket.entity.TicketReviewDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.exception.ImportFailedException;
import vn.tsolve.ticket.exception.ManualEntryRejectedException;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.repository.ImportBatchRepository;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.repository.TicketEvidenceRepository;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketReviewDecisionRepository;
import vn.tsolve.ticket.service.ManualEntryService;
import vn.tsolve.ticket.service.ProjectAccess;

/**
 * Manual entry (UC-22) and its Drafts. The Ticket, its Evidence, its approve decision, the Completed Import Batch, the
 * deletion of its Draft, the "tickets published" event and the audit record share one transaction; when it fails,
 * the Failed Import Batch is written afterwards in a transaction of its own, as Confirm does. Files are stored before
 * that transaction and the files a Draft no longer keeps are deleted after it (design: Storage vs. transaction).
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
    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final Clock clock;

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
            AuditService audit,
            ImportPreviewRepository previews,
            ImportPreviewRowRepository previewRows,
            Clock clock) {
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
        this.previews = previews;
        this.previewRows = previewRows;
        this.clock = clock;
    }

    /** The Draft files a request keeps and the storage keys of the ones it removes; no Draft = nothing kept. */
    private record Kept(ImportPreview draft, List<DraftEvidence> files, List<String> removedKeys) {
        static final Kept NONE = new Kept(null, List.of(), List.of());
    }

    private record Saved<T>(T value, List<String> removedKeys) {}

    @Override
    public ManualEntryResponse save(TSolveUser user, String entry, List<MultipartFile> files) {
        // order (design Save): field checks, Project access, the Draft, the files, then the Ticket ID — a rejected
        // entry saves nothing, stores no file and records no Import Batch
        ManualEntryRequest request = read(entry);
        ManualEntryValidator.Entry valid = validator.validate(request);
        String caller = user.getName();
        requireAccess(caller, valid.projectId());
        // only for the checks below; the transaction reads the Draft again under its lock (AC-007.9)
        Kept before = kept(caller, request, previews::findByIdAndOwner);
        evidence.check(files, before.files().size());
        if (!tickets.findExistingTicketIds(List.of(valid.ticketId())).isEmpty()) {
            throw ManualEntryRejectedException.ticketIdTaken(valid.ticketId());
        }

        List<EvidenceFiles.Stored> stored;
        try {
            stored = evidence.store(files);
        } catch (RuntimeException e) {
            fail(valid.projectId(), caller, e);
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Saved<UUID> saved;
        try {
            saved = transaction.execute(status -> {
                Kept kept = kept(caller, request, previews::lockByIdAndOwner);
                return new Saved<>(insert(user, valid, kept, stored), kept.removedKeys());
            });
        } catch (NotFoundException e) {
            // the Draft or a kept file is gone (AC-007.8, AC-007.9): not a failure of the import, no batch
            evidence.delete(stored);
            throw e;
        } catch (RuntimeException e) {
            evidence.delete(stored);
            if (e instanceof DataIntegrityViolationException && namesTicketIdConstraint(e)) {
                // another request saved this Ticket ID after the check above (AC-006.8): not a failure, no batch
                throw ManualEntryRejectedException.ticketIdTaken(valid.ticketId());
            }
            fail(valid.projectId(), caller, e);
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        evidence.deleteKeys(saved.removedKeys());
        return new ManualEntryResponse(saved.value(), new MessageRef("MSG32", Map.of("created", 1, "skipped", 0)));
    }

    @Override
    public DraftSavedResponse saveDraft(TSolveUser user, String entry, List<MultipartFile> files) {
        // a Draft checks only its Project and its files (AC-007.1, AC-007.2)
        ManualEntryRequest request = read(entry);
        if (request.projectId() == null) {
            throw ManualEntryRejectedException.missing(List.of("projectId"));
        }
        String caller = user.getName();
        requireAccess(caller, request.projectId());
        Kept before = kept(caller, request, previews::findByIdAndOwner);
        evidence.check(files, before.files().size());

        List<EvidenceFiles.Stored> stored;
        try {
            stored = evidence.store(files);
        } catch (RuntimeException e) {
            log.warn(
                    "Storing the files of a Manual Draft failed: {}",
                    e.getClass().getSimpleName());
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Instant now = Instant.now(clock);
        Saved<Long> saved;
        try {
            saved = transaction.execute(status -> writeDraft(caller, request, stored, now));
        } catch (NotFoundException e) {
            evidence.delete(stored);
            throw e;
        } catch (RuntimeException e) {
            evidence.delete(stored);
            log.warn("Saving a Manual Draft failed: {}", e.getClass().getSimpleName());
            throw new ImportFailedException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        evidence.deleteKeys(saved.removedKeys());
        return new DraftSavedResponse(saved.value(), now, new MessageRef("MSG41", Map.of()));
    }

    @Override
    public ManualDraftResponse getDraft(String user, long previewId) {
        ImportPreview draft = manualDraft(previews.findByIdAndOwner(previewId, user));
        ImportPreviewRow.Values values = previewRows
                .findById(new ImportPreviewRow.Id(draft.getId(), 1))
                .orElseThrow(ManualEntryServiceImpl::notFound)
                .getValues();
        return new ManualDraftResponse(
                draft.getId(),
                draft.getProjectId(),
                values.ticketId(),
                values.title(),
                values.description(),
                values.resolvedDate(),
                values.symptoms(),
                values.rootCause(),
                values.resolutionSteps(),
                values.workaround(),
                values.scope(),
                draft.getEvidence().stream()
                        .map(f -> new DraftEvidenceResponse(f.id(), f.fileName(), f.fileType(), f.fileSize()))
                        .toList(),
                draft.getDraftSavedAt());
    }

    private Saved<Long> writeDraft(
            String caller, ManualEntryRequest request, List<EvidenceFiles.Stored> stored, Instant now) {
        ImportPreviewRow.Values values = new ImportPreviewRow.Values(
                request.ticketId(),
                request.title(),
                request.description(),
                null,
                request.resolvedDate(),
                request.symptoms(),
                request.rootCause(),
                request.resolutionSteps(),
                request.workaround(),
                request.scope());
        List<DraftEvidence> added = stored.stream()
                .map(f -> new DraftEvidence(UUID.randomUUID(), f.key(), f.fileName(), f.fileType(), f.fileSize()))
                .toList();
        if (request.draftId() == null) {
            ImportPreview draft = ImportPreview.manualDraft(caller, request.projectId(), now);
            draft.replaceEvidence(added);
            long id = previews.save(draft).getId();
            previewRows.save(ImportPreviewRow.manualEntry(id, values));
            return new Saved<>(id, List.of());
        }
        Kept kept = kept(caller, request, previews::lockByIdAndOwner);
        ImportPreview draft = kept.draft();
        draft.saveManualDraftAgain(request.projectId(), now);
        draft.replaceEvidence(
                Stream.concat(kept.files().stream(), added.stream()).toList());
        previewRows
                .findById(new ImportPreviewRow.Id(draft.getId(), 1))
                .orElseThrow(ManualEntryServiceImpl::notFound)
                .replaceValues(values);
        return new Saved<>(draft.getId(), kept.removedKeys());
    }

    /**
     * The Draft the request names, read with {@code lookup}, and the files it keeps: {@code keepEvidenceIds} null
     * keeps them all; an id the Draft does not have is 404, as is a Draft of another user or not Manual (AC-007.8,
     * AC-007.9).
     */
    private Kept kept(
            String caller, ManualEntryRequest request, BiFunction<Long, String, Optional<ImportPreview>> lookup) {
        if (request.draftId() == null) {
            return Kept.NONE;
        }
        ImportPreview draft = manualDraft(lookup.apply(request.draftId(), caller));
        List<DraftEvidence> files = draft.getEvidence();
        if (request.keepEvidenceIds() == null) {
            return new Kept(draft, files, List.of());
        }
        Set<UUID> keep = new HashSet<>(request.keepEvidenceIds());
        if (!files.stream().map(DraftEvidence::id).toList().containsAll(keep)) {
            throw notFound();
        }
        return new Kept(
                draft,
                files.stream().filter(f -> keep.contains(f.id())).toList(),
                files.stream()
                        .filter(f -> !keep.contains(f.id()))
                        .map(DraftEvidence::storageKey)
                        .toList());
    }

    private static ImportPreview manualDraft(Optional<ImportPreview> found) {
        return found.filter(p -> p.getSource() == TicketSource.MANUAL).orElseThrow(ManualEntryServiceImpl::notFound);
    }

    private void requireAccess(String caller, UUID projectId) {
        if (!projects.managesActive(caller, projectId)) {
            throw new ProjectAccessDeniedException();
        }
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

    private UUID insert(
            TSolveUser user, ManualEntryValidator.Entry entry, Kept kept, List<EvidenceFiles.Stored> stored) {
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
        List<TicketEvidence> rows = Stream.concat(
                        kept.files().stream()
                                .map(f -> TicketEvidence.of(
                                        ticketId, f.storageKey(), f.fileName(), f.fileType(), f.fileSize())),
                        stored.stream()
                                .map(f ->
                                        TicketEvidence.of(ticketId, f.key(), f.fileName(), f.fileType(), f.fileSize())))
                .toList();
        if (!rows.isEmpty()) {
            evidenceRows.saveAll(rows);
        }
        decisions.save(TicketReviewDecision.approve(ticketId, caller));
        if (kept.draft() != null) {
            // in this transaction: a failed save leaves the Draft as it was (AC-007.6); rows go through ON DELETE
            // CASCADE
            previews.delete(kept.draft());
        }
        // inside the transaction: @TransactionalEventListener(AFTER_COMMIT) listeners get it once this commits
        events.publishEvent(new TicketsPublishedEvent(List.of(ticketId)));
        audit.record(
                AuditAction.IMPORT, "PROJECT", entry.projectId(), Map.of("importBatchId", batch.getId(), "created", 1));
        return ticketId;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("MSG04", Map.of());
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
