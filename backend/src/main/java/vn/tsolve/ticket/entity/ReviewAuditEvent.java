package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Write-only mapping of the existing shared append-only audit table. */
@Entity
@Table(name = "audit_event")
public class ReviewAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewAuditEvent() {}

    public ReviewAuditEvent(String actor, String action, String resource, Instant at) {
        this.actor = actor;
        this.action = action;
        this.resource = resource;
        result = "SUCCESS";
        createdAt = at;
    }
}
