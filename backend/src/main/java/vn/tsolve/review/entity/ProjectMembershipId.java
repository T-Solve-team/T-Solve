package vn.tsolve.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProjectMembershipId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    protected ProjectMembershipId() {}

    public ProjectMembershipId(UUID userId, UUID projectId) {
        this.userId = userId;
        this.projectId = projectId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProjectMembershipId id
                && Objects.equals(userId, id.userId)
                && Objects.equals(projectId, id.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, projectId);
    }
}
