package vn.tsolve.ticket.service;

import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.ManualTicketDraft;
import vn.tsolve.ticket.dto.ManualTicketRequest;

/** A ticket with its Solution entered by hand by a Project Manager (UC-22, STORY-006). */
public interface ManualTicketService {

    /** Saves the entry as a Published Ticket with a Completed Import Batch of source Manual (MSG32). */
    ImportResultResponse create(TSolveUser user, ManualTicketRequest request);

    /** Saves the entry as a Manual Draft, or updates the Draft named by draftId; only the Project is checked (MSG41). */
    DraftSavedResponse saveDraft(TSolveUser user, ManualTicketRequest request);

    /** One of the caller's Manual Drafts with every field as saved. */
    ManualTicketDraft getDraft(TSolveUser user, long previewId);
}
