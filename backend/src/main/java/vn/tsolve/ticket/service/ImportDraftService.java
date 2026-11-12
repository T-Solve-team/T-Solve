package vn.tsolve.ticket.service;

import java.util.List;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ImportDraftSummary;

/** Drafts of a Jira import (GBR-26, STORY-004). Reopening one is the preview endpoint itself (AC-004.3). */
public interface ImportDraftService {

    /** Marks the caller's Jira preview a Draft, or moves the save time of one saved before; no validation. */
    DraftSavedResponse save(String user, long previewId);

    /** The caller's own Drafts, newest first. */
    List<ImportDraftSummary> list(String user);

    /** Deletes one of the caller's Drafts with its rows. */
    void delete(String user, long previewId);
}
