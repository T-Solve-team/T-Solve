package vn.tsolve.jiraapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The issue text the Jira App read with requestJira, for the AI draft or the prefill (jira-app AC-002.1, 002.2), and
 * whether the form opens as the resubmission of a ticket in Changes requested (AC-012.2); a missing {@code resubmit}
 * is false, so the background script's Done check never opens a resubmission.
 */
public record SolutionFormRequest(
        @NotNull @Size(max = 255) String summary,
        @NotNull @Size(max = 32767) String description,
        boolean resubmit) {

    public SolutionFormRequest(String summary, String description) {
        this(summary, description, false);
    }
}
