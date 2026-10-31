package vn.tsolve.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The part of a Forge {@code avi:jira:created:issue} event that T-Solve reads (contract {@code IssueCreatedPayload});
 * other fields are ignored. The key fits {@code suggestion_result.jira_issue_key} (AC-003.6).
 */
public record IssueCreatedPayload(@NotNull @Valid Issue issue) {

    public record Issue(@NotBlank @Size(max = 50) String key, @NotNull @Valid Fields fields) {}

    public record Fields(@NotNull @Valid Project project) {}

    public record Project(@NotBlank String key) {}

    public String issueKey() {
        return issue.key();
    }

    public String projectKey() {
        return issue.fields().project().key();
    }
}
