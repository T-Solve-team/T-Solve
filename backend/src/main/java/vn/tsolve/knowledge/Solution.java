package vn.tsolve.knowledge;

import jakarta.persistence.*;
import java.time.Instant;
import vn.tsolve.common.exception.BusinessRuleException;

@Entity
@Table(name = "solution")
public class Solution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String symptoms;

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(columnDefinition = "text")
    private String steps;

    @Column(columnDefinition = "text")
    private String workaround;

    @Column(columnDefinition = "text")
    private String applicability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SolutionStatus status = SolutionStatus.DRAFT;

    @Column(name = "review_due_at")
    private Instant reviewDueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Solution() {}

    public Solution(Long workspaceId, String title) {
        this.workspaceId = workspaceId;
        this.title = title;
    }

    public void edit(SolutionRequest r) {
        if (status != SolutionStatus.DRAFT) {
            throw new BusinessRuleException("MSG06");
        }
        this.title = r.title();
        this.symptoms = r.symptoms();
        this.rootCause = r.rootCause();
        this.steps = r.steps();
        this.workaround = r.workaround();
        this.applicability = r.applicability();
        this.reviewDueAt = r.reviewDueAt();
        this.updatedAt = Instant.now();
    }

    public void transitionTo(SolutionStatus next) {
        if (!status.canMoveTo(next)) {
            throw new BusinessRuleException("MSG06");
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public String getRootCause() {
        return rootCause;
    }

    public String getSteps() {
        return steps;
    }

    public String getWorkaround() {
        return workaround;
    }

    public String getApplicability() {
        return applicability;
    }

    public SolutionStatus getStatus() {
        return status;
    }

    public Instant getReviewDueAt() {
        return reviewDueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
