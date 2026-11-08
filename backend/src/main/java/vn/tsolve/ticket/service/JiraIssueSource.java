package vn.tsolve.ticket.service;

import java.time.Instant;
import java.time.LocalDate;
import vn.tsolve.ticket.exception.JiraUnavailableException;

/**
 * Reads resolved issues from the Workspace's Jira (Jira import, UC-20). The Workspace service account behind it comes
 * from the environment until the workspace module stores it (spec D2).
 */
public interface JiraIssueSource {

    /**
     * One page of the issues of a Jira project resolved from {@code from} to {@code to} (both included, Vietnam time),
     * oldest first. {@code pageToken} null = the first page. No Jira call starts, and no retry waits, past
     * {@code deadline} (AC-001.13).
     *
     * @throws JiraUnavailableException when the connection is not configured, Jira gives no usable answer or the
     *     deadline has passed
     */
    JiraIssuePage resolvedIssues(String projectKey, LocalDate from, LocalDate to, String pageToken, Instant deadline);
}
