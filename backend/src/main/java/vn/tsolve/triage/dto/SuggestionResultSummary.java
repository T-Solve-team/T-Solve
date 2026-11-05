package vn.tsolve.triage.dto;

import java.time.Instant;

public record SuggestionResultSummary(
        String issueKey,
        String jiraIssueUrl,
        SuggestionProject project,
        Instant createdAt,
        int ticketSourceCount,
        int wikiSourceCount,
        String suggestedSolutionExcerpt,
        String reporterAnswer) {}
