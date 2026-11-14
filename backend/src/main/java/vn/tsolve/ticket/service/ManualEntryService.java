package vn.tsolve.ticket.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.ticket.dto.DraftSavedResponse;
import vn.tsolve.ticket.dto.ManualDraftResponse;
import vn.tsolve.ticket.dto.ManualEntryResponse;

public interface ManualEntryService {

    /**
     * Saves one ticket its Project Manager entered by hand as a Published Ticket with an approve decision by them
     * (UC-22, AC-006.1…AC-006.11). {@code entry} is the JSON text of a {@code ManualEntryRequest}; {@code files} are
     * its Evidence files, possibly none.
     */
    ManualEntryResponse save(TSolveUser user, String entry, List<MultipartFile> files);

    /**
     * Saves the entry as the caller's Manual Draft, or updates the Draft {@code draftId} names (AC-007.1, AC-007.2,
     * AC-007.5, AC-007.9); only the Project and the files are checked.
     */
    DraftSavedResponse saveDraft(TSolveUser user, String entry, List<MultipartFile> files);

    /** The caller's Manual Draft as saved (AC-007.4); another user's, a missing or a non-Manual one is 404 (AC-007.8). */
    ManualDraftResponse getDraft(String user, long previewId);
}
