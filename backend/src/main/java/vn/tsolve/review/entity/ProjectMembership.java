package vn.tsolve.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** A row of {@code project_membership}; Review only reads it, identity administers it. */
@Entity
@Table(name = "project_membership")
public class ProjectMembership {

    @EmbeddedId
    private ProjectMembershipId id;

    @Column(name = "role_in_project", nullable = false, length = 30)
    private String roleInProject;

    @Column(name = "date_added", nullable = false)
    private Instant dateAdded;

    protected ProjectMembership() {}
}
