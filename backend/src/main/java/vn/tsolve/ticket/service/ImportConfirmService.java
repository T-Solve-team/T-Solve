package vn.tsolve.ticket.service;

import vn.tsolve.ticket.dto.ImportResultResponse;

public interface ImportConfirmService {

    /** Imports the remaining Valid rows of the caller's preview as Published tickets, all or nothing (UC-21 7–9). */
    ImportResultResponse confirm(String user, long previewId);
}
