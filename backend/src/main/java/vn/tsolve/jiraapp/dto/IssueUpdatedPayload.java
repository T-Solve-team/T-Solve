package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/**
 * The part of a Forge {@code avi:jira:updated:issue} event that T-Solve reads (contract {@code IssueUpdatedPayload});
 * every part may be missing, and an event that cannot be read changes nothing (jira-app AC-005.10).
 */
public record IssueUpdatedPayload(String atlassianId, Issue issue, Changelog changelog) {

    public record Issue(String key, Fields fields) {}

    public record Fields(String summary, JsonNode description, Status status, Assignee assignee) {}

    public record Status(StatusCategory statusCategory) {}

    public record StatusCategory(String key) {}

    public record Assignee(String accountId) {}

    public record Changelog(List<Item> items) {}

    public record Item(String field, String from, String to) {}

    /** The category key Jira gives the Done status category. */
    public static final String DONE = "done";

    public Optional<String> issueKey() {
        return issue == null ? Optional.empty() : text(issue.key());
    }

    /** The changelog's status change, when the event has one. */
    public Optional<Item> statusChange() {
        if (changelog == null || changelog.items() == null) {
            return Optional.empty();
        }
        return changelog.items().stream()
                .filter(item -> item != null && "status".equals(item.field()))
                .findFirst();
    }

    /** The issue's status category after the change; empty when the event does not carry it. */
    public Optional<String> statusCategory() {
        Fields fields = fields();
        if (fields == null || fields.status() == null || fields.status().statusCategory() == null) {
            return Optional.empty();
        }
        return text(fields.status().statusCategory().key());
    }

    /** The assignee's Jira account, else the account that made the change (AC-005.7). */
    public Optional<String> draftOwner() {
        Fields fields = fields();
        Optional<String> assignee = fields == null || fields.assignee() == null
                ? Optional.empty()
                : text(fields.assignee().accountId());
        return assignee.or(() -> text(atlassianId));
    }

    public String summary() {
        Fields fields = fields();
        return fields == null || fields.summary() == null ? "" : fields.summary();
    }

    public JsonNode description() {
        Fields fields = fields();
        return fields == null ? null : fields.description();
    }

    private Fields fields() {
        return issue == null ? null : issue.fields();
    }

    private static Optional<String> text(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }
}
