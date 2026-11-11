package vn.tsolve.ticket.entity;

import java.time.Instant;

/** A comment of a Jira issue as it was at pull time, as plain text; author null when Jira gives none. */
public record IssueComment(String author, Instant created, String body) {}
