package vn.tsolve.triage.dto;

/**
 * A Project the caller manages: {@code id} is the database key of {@code project}, {@code publicId} the id the web
 * app knows (the session's {@code ProjectMembership.projectId}) and {@code key} its Jira project key.
 */
public record ManagedProject(long id, String publicId, String key) {}
