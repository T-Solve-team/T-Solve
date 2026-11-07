package vn.tsolve.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import vn.tsolve.common.audit.AuditAction;

@Entity
@Immutable
@Table(name = "audit_log_entry")
public class AuditLogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_log_entry_id")
    private UUID id;

    private String actorEmail;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    private String targetType;
    private UUID targetId;
    private Instant createdAt;

    protected AuditLogEntry() {}

    public UUID getId() {
        return id;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
