package vn.tsolve.ticket.entity;

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
import vn.tsolve.ticket.dto.SolutionSections;

/** A Solution Form Draft: one per creator and Jira issue, stored unvalidated (jira-app STORY-005, GBR-26). */
@Entity
@Table(name = "solution_draft")
public class SolutionDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "solution_draft_id")
    private UUID id;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "jira_issue_key", nullable = false, updatable = false)
    private String issueKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private SolutionSections content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SolutionDraft() {}

    public SolutionDraft(UUID creatorId, UUID projectId, String issueKey, SolutionSections content, Instant now) {
        this.creatorId = creatorId;
        this.projectId = projectId;
        this.issueKey = issueKey;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Replaces the saved sections; a user has only one Draft per issue (AC-005.1). */
    public void replace(SolutionSections newContent, Instant now) {
        this.content = newContent;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public SolutionSections getContent() {
        return content;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
