package vn.tsolve.triage.dto;

import java.time.Instant;
import java.util.List;

public record SuggestionResultDetail(
        String issueKey,
        String jiraIssueUrl,
        SuggestionProject project,
        Instant createdAt,
        int ticketSourceCount,
        int wikiSourceCount,
        String suggestedSolutionExcerpt,
        String reporterAnswer,
        String suggestedSolution,
        List<SuggestionWikiSource> wikiSources) {}
