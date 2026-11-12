package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ImportDraftSummary;
import vn.tsolve.ticket.dto.MessageRef;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.entity.PreviewRowStatus;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.repository.ImportPreviewRepository;
import vn.tsolve.ticket.repository.ImportPreviewRowRepository;
import vn.tsolve.ticket.service.ImportDraftService;
import vn.tsolve.ticket.service.ProjectAccess;

@Service
public class ImportDraftServiceImpl implements ImportDraftService {

    private final ImportPreviewRepository previews;
    private final ImportPreviewRowRepository previewRows;
    private final ProjectAccess projects;
    private final Clock clock;

    public ImportDraftServiceImpl(
            ImportPreviewRepository previews,
            ImportPreviewRowRepository previewRows,
            ProjectAccess projects,
            Clock clock) {
        this.previews = previews;
        this.previewRows = previewRows;
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DraftSavedResponse save(String user, long previewId) {
        ImportPreview preview = jiraPreview(user, previewId);
        // a preview request of a Project the caller no longer manages is 403 (AC-002.9)
        if (!projects.managesActive(user, preview.getProjectId())) {
            throw new ProjectAccessDeniedException();
        }
        Instant now = Instant.now(clock);
        preview.saveAsDraft(now);
        return new DraftSavedResponse(preview.getId(), now, new MessageRef("MSG41", Map.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImportDraftSummary> list(String user) {
        // ponytail: one count query per Draft; a user keeps a handful, group the counts if that ever grows
        return previews.findByOwnerAndDraftSavedAtIsNotNullOrderByDraftSavedAtDesc(user).stream()
                .map(p -> new ImportDraftSummary(
                        p.getId(),
                        p.getSource(),
                        p.getProjectId(),
                        previewRows.countByIdPreviewIdAndStatusNot(p.getId(), PreviewRowStatus.REMOVED),
                        p.getDraftSavedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(String user, long previewId) {
        ImportPreview preview = jiraPreview(user, previewId);
        if (!preview.isDraft()) {
            throw notFound();
        }
        // rows go through ON DELETE CASCADE
        previews.delete(preview);
    }

    /** Another user's preview, a CSV one or none at all is "not found", so ids cannot be probed (AC-004.5). */
    private ImportPreview jiraPreview(String user, long previewId) {
        return previews.findByIdAndOwner(previewId, user)
                .filter(p -> p.getSource() == TicketSource.JIRA_IMPORT)
                .orElseThrow(ImportDraftServiceImpl::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("MSG04", Map.of());
    }
}
