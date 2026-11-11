package vn.tsolve.jiraapp.service;

import java.util.function.Function;
import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.JiraIssueContext;

/**
 * The one access rule of the Jira App on an issue, shared with `ticket-review`'s feedback endpoint: the Workspace from
 * the verified site, the linked Account from the FIT principal, the Project from the issue key, and the membership.
 */
public interface JiraIssueAccess {

    /**
     * Binds the Workspace registered for the caller's site and runs {@code work} with the issue's context. A call
     * without a Jira user, or from a site with no registered Workspace, is refused with 403 MSG08 before any read.
     */
    <T> T inIssue(JiraAppPrincipal caller, String issueKey, Function<JiraIssueContext, T> work);
}
