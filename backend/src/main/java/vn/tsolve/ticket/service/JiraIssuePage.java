package vn.tsolve.ticket.service;

import java.util.List;

/** A page of Jira issues; {@code nextPageToken} null = the last page. */
public record JiraIssuePage(List<JiraIssue> issues, String nextPageToken) {}
