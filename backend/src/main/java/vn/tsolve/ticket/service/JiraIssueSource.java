package vn.tsolve.ticket.service;

import java.time.LocalDate;
import vn.tsolve.ticket.exception.JiraUnavailableException;

/**
 * Reads resolved issues from the Workspace's Jira (Jira import, UC-20). The Workspace service account behind it comes
 * from the environment until the workspace module stores it (spec D2).
 */
public interface JiraIssueSource {

    /**
     * One page of the issues of a Jira project resolved from {@code from} to {@code to} (both included, Vietnam time),
     * oldest first. {@code pageToken} null = the first page.
     *
     * @throws JiraUnavailableException when the connection is not configured or Jira gives no usable answer
     */
    JiraIssuePage resolvedIssues(String projectKey, LocalDate from, LocalDate to, String pageToken);
}
