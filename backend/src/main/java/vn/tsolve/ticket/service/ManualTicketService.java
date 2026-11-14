package vn.tsolve.ticket.service;

import java.util.List;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.EvidenceUpload;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.ManualTicketDraft;
import vn.tsolve.ticket.dto.ManualTicketRequest;

/** A ticket with its Solution entered by hand by a Project Manager (UC-22, STORY-006). */
public interface ManualTicketService {

    /** Saves the entry as a Published Ticket with a Completed Import Batch of source Manual (MSG32). */
    ImportResultResponse create(TSolveUser user, ManualTicketRequest request, List<EvidenceUpload> files);

    default ImportResultResponse create(TSolveUser user, ManualTicketRequest request) {
        return create(user, request, List.of());
    }

    /** Saves or updates a Manual Draft (MSG41); checks the Project and Evidence, keeping other fields as typed. */
    DraftSavedResponse saveDraft(TSolveUser user, ManualTicketRequest request, List<EvidenceUpload> files);

    default DraftSavedResponse saveDraft(TSolveUser user, ManualTicketRequest request) {
        return saveDraft(user, request, List.of());
    }

    /** One of the caller's Manual Drafts with every field as saved. */
    ManualTicketDraft getDraft(TSolveUser user, long previewId);
}
