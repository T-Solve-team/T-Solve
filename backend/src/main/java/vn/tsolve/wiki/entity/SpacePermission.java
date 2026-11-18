package vn.tsolve.wiki.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "space_permission")
public class SpacePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "space_permission_id")
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected SpacePermission() {}

    public SpacePermission(long spaceId, Long departmentId, UUID userId, UUID grantedBy, Instant grantedAt) {
        this.spaceId = spaceId;
        this.departmentId = departmentId;
        this.userId = userId;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public UUID getId() {
        return id;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
