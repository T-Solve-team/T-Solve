package vn.tsolve.ticket.service;

import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.ImportResultResponse;
import vn.tsolve.ticket.dto.ManualTicketRequest;

/** A ticket with its Solution entered by hand by a Project Manager (UC-22, STORY-006). */
public interface ManualTicketService {

    /** Saves the entry as a Published Ticket with a Completed Import Batch of source Manual (MSG32). */
    ImportResultResponse create(TSolveUser user, ManualTicketRequest request);
}
