package vn.tsolve.tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The AI Solution Suggestion of one Jira issue; the tracker creates the row when the issue is created (AC-003.1)
 * and the suggestion owner fills it later. Not mapped yet, so the insert leaves them to the database:
 * {@code sources_used} (default {@code []}), {@code suggested_solution} and {@code reporter_answer} (NULL).
 */
@Entity
@Table(name = "suggestion_result")
public class SuggestionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_id")
    private Long id;

    // The Project's id; no JPA association across modules (AGENTS §1).
    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "jira_issue_key", nullable = false, updatable = false)
    private String jiraIssueKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SuggestionResult() {}

    public SuggestionResult(Long projectId, String jiraIssueKey, Instant now) {
        this.projectId = projectId;
        this.jiraIssueKey = jiraIssueKey;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getJiraIssueKey() {
        return jiraIssueKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
