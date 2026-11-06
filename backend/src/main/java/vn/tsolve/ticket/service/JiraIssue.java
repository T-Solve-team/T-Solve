package vn.tsolve.ticket.service;

import java.time.Instant;
import java.util.List;
import vn.tsolve.ticket.entity.IssueComment;

/** A resolved Jira issue as the import keeps it (AC-001.2); description and assignee may be null. */
public record JiraIssue(
        String key,
        String summary,
        String description,
        String assignee,
        Instant resolvedAt,
        String url,
        List<IssueComment> comments) {}
