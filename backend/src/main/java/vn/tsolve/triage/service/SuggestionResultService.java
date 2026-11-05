package vn.tsolve.triage.service;

import org.springframework.data.domain.Page;
import vn.tsolve.triage.dto.HistoryQuery;
import vn.tsolve.triage.dto.SuggestionResultDetail;
import vn.tsolve.triage.dto.SuggestionResultSummary;

/** The AI Solution Suggestion results a Project Manager may read: screen 10.1 and its 10.2 popup. */
public interface SuggestionResultService {

    /** One page of the results of the user's managed Projects, newest first (AC-003.1 – AC-003.6). */
    Page<SuggestionResultSummary> history(String user, HistoryQuery query);

    /**
     * The result of one issue of a managed Project; {@code NotFoundException} (MSG04) for every miss, including a
     * caller who is no Project Manager, so the answer never tells whether a result exists (AC-004.2).
     */
    SuggestionResultDetail detail(String user, boolean projectManager, String issueKey);
}
