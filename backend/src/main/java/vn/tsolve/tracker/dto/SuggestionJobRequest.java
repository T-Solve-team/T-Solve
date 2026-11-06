package vn.tsolve.tracker.dto;

/**
 * What one suggestion job needs (AC-011.1). The issue text travels with the job because the job cannot read Jira:
 * the tracker keeps no Jira token (Non-Functional 3). Never log {@code summary} or {@code description}.
 */
public record SuggestionJobRequest(String issueKey, long projectId, String summary, String description) {}
