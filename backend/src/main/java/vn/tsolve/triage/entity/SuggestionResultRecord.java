package vn.tsolve.triage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only view of the tracker's {@code suggestion_result} for the history; triage never writes the table, so the
 * tracker's own entity and changeset stay its own. {@code sources_used} is kept as raw JSON: an item the writer got
 * wrong must not fail the whole row (AC-004.4).
 */
@Entity
@Immutable
@Table(name = "suggestion_result")
public class SuggestionResultRecord {

    @Id
    @Column(name = "suggestion_id")
    private UUID id;

    @Column(name = "project_id", nullable = false)
    // The Project's public_id (tracker's 019-tracker-suggestion-uuid-keys).
    private UUID projectId;

    @Column(name = "jira_issue_key", nullable = false)
    private String jiraIssueKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources_used", nullable = false, columnDefinition = "jsonb")
    private String sourcesUsed;

    @Column(name = "suggested_solution", columnDefinition = "text")
    private String suggestedSolution;

    @Column(name = "reporter_answer")
    private String reporterAnswer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SuggestionResultRecord() {}

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getJiraIssueKey() {
        return jiraIssueKey;
    }

    public String getSourcesUsed() {
        return sourcesUsed;
    }

    public String getSuggestedSolution() {
        return suggestedSolution;
    }

    public String getReporterAnswer() {
        return reporterAnswer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
