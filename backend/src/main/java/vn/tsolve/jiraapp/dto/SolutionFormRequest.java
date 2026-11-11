package vn.tsolve.jiraapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The issue text the Jira App read with requestJira, for the AI draft or the prefill (jira-app AC-002.1, 002.2). */
public record SolutionFormRequest(
        @NotNull @Size(max = 255) String summary, @NotNull @Size(max = 32767) String description) {}
