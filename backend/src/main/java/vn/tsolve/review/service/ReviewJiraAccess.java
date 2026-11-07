package vn.tsolve.review.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import vn.tsolve.common.security.JiraAppPrincipal;

/** Trusted integration with Workspace routing, linked Accounts and Jira issue permissions. */
public interface ReviewJiraAccess {

    /**
     * Resolve the verified cloudId to its registered Workspace and bind its database for the callback.
     * Verify an active linked Account and access to this issue using trusted Jira context or Jira's user API.
     * Reject missing or unverifiable bindings before invoking the callback. Never use a caller-selected Workspace.
     */
    <T> T inWriterIssue(JiraAppPrincipal caller, String issueKey, Function<WriterIssue, T> read);

    /** Read the stored display name in the currently bound Workspace, including former managers. */
    Optional<String> accountName(UUID accountId);

    record WriterIssue(UUID accountId, UUID projectId) {}
}
