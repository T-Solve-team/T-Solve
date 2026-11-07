package vn.tsolve.tracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The AI Solution Suggestion of one Jira issue; the tracker creates the row when the issue is created (AC-003.1)
 * with the issue text and a job reference (AC-011.1); its suggestion job fills sources and suggested solution with a
 * conditional update (AC-011.4,
 * {@code SuggestionResultRepository.fillSuggestion}). {@code reporter_answer} is not mapped yet, so the insert leaves
 * it NULL.
 */
@Entity
@Table(name = "suggestion_result")
public class SuggestionResult {

    static final String NO_SOURCES = "[]";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_id")
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "jira_issue_key", nullable = false, updatable = false)
    private String jiraIssueKey;

    // Raw JSON in the shape triage reads: [{"type", "id", "title", "similarity"}].
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources_used", nullable = false, columnDefinition = "jsonb")
    private String sourcesUsed = NO_SOURCES;

    @Column(name = "suggested_solution", columnDefinition = "text")
    private String suggestedSolution;

    // What the worker's job carries instead of the issue text (AC-011.1).
    @Column(name = "job_ref", nullable = false, updatable = false, unique = true)
    private UUID jobRef;

    @Column(name = "issue_summary", columnDefinition = "text", updatable = false)
    private String issueSummary;

    @Column(name = "issue_description", columnDefinition = "text", updatable = false)
    private String issueDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SuggestionResult() {}

    public SuggestionResult(
            Long projectId, String jiraIssueKey, String issueSummary, String issueDescription, Instant now) {
        this.projectId = projectId;
        this.jiraIssueKey = jiraIssueKey;
        this.jobRef = UUID.randomUUID();
        this.issueSummary = issueSummary;
        this.issueDescription = issueDescription;
        this.createdAt = now;
    }

    public boolean hasSuggestion() {
        return suggestedSolution != null;
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

    public UUID getJobRef() {
        return jobRef;
    }

    public String getIssueSummary() {
        return issueSummary;
    }

    public String getIssueDescription() {
        return issueDescription;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
