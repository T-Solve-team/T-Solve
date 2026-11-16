package vn.tsolve.ticket.service;

import java.time.Instant;
import java.time.LocalDate;
import vn.tsolve.ticket.exception.JiraUnavailableException;

/**
 * Reads resolved issues from the Workspace's Jira (Jira import, UC-20) with the Workspace's service account (spec D2,
 * KAN-345).
 */
public interface JiraIssueSource {

    /**
     * One pull of the issues of a Jira project resolved from {@code from} to {@code to} (both included, Vietnam time),
     * oldest first. Every page of it uses the account read when the pull starts (AC-001.12, BUG-311). No Jira call
     * starts, and no retry waits, past {@code deadline} (AC-001.13).
     *
     * @throws JiraUnavailableException when the connection is not configured, Jira gives no usable answer or the
     *     deadline has passed; also from {@link Pull#page}
     */
    Pull pull(String projectKey, LocalDate from, LocalDate to, Instant deadline);

    /** The pages of one pull; {@code pageToken} null = the first page. */
    @FunctionalInterface
    interface Pull {
        JiraIssuePage page(String pageToken);
    }
}
