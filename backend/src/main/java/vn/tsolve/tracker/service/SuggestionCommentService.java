package vn.tsolve.tracker.service;

import java.util.UUID;

/** The MSG80 comment of a stored suggestion (STORY-012); only the suggestion job calls it. */
public interface SuggestionCommentService {

    /**
     * Posts the suggestion on the issue as MSG80 with the Workspace's service account and records the comment's id
     * (AC-012.1, AC-012.3); stores the id of a comment already posted instead of posting twice (AC-012.5). With no
     * service account or a call Jira rejects, it posts nothing and returns (AC-012.8). Throws {@code
     * JiraUnavailableException} when a retry may help (AC-012.7).
     */
    void post(UUID suggestionId, String issueKey, String suggestedSolution);
}
