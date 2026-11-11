package vn.tsolve.tracker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The part of a Forge {@code avi:jira:created:issue} event that T-Solve reads (contract {@code IssueCreatedPayload});
 * other fields are ignored. The key fits {@code suggestion_result.jira_issue_key} (AC-003.6). Summary, description
 * (ADF) and labels are optional: an event without them is still recorded (STORY-011).
 */
public record IssueCreatedPayload(@NotNull @Valid Issue issue) {

    public record Issue(@NotBlank @Size(max = 50) String key, @NotNull @Valid Fields fields) {}

    public record Fields(@NotNull @Valid Project project, String summary, JsonNode description, List<String> labels) {}

    public record Project(@NotBlank String key) {}

    public String issueKey() {
        return issue.key();
    }

    public String projectKey() {
        return issue.fields().project().key();
    }

    /** The summary, or an empty string when the event has none. */
    public String summary() {
        String summary = issue.fields().summary();
        return summary == null ? "" : summary;
    }

    /** Whether the issue carries this label; an event without labels has none. */
    public boolean hasLabel(String label) {
        List<String> labels = issue.fields().labels();
        return labels != null && labels.contains(label);
    }

    /** The description as ADF, or {@code null} when the event has none. */
    public JsonNode description() {
        return issue.fields().description();
    }
}
