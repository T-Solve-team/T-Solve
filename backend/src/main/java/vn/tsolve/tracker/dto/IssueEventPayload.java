package vn.tsolve.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * The part of a Forge Jira issue event that T-Solve reads (contract {@code IssueEventPayload}); other fields are
 * ignored. Forge documents {@code timestamp} as a string of epoch milliseconds.
 */
public record IssueEventPayload(
        @NotNull @Valid Issue issue, @NotBlank @Pattern(regexp = "^\\d{1,19}$") String timestamp, Changelog changelog) {

    public record Issue(@NotBlank String id, @NotBlank String key) {}

    public record Changelog(List<Item> items) {}

    public record Item(String fieldId) {}

    // 19 digits can still exceed a long; such a body is invalid too (AC-003.6).
    @AssertTrue(message = "timestamp is out of range")
    public boolean isTimestampInRange() {
        if (timestamp == null || !timestamp.matches("^\\d{1,19}$")) {
            return true; // reported by @NotBlank / @Pattern
        }
        try {
            Long.parseLong(timestamp);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public long timestampMillis() {
        return Long.parseLong(timestamp);
    }
}
