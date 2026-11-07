package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A permanent decision on a submitted Solution. */
@Entity
@Table(name = "review_decision")
public class ReviewDecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_decision_id")
    private UUID id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "project_manager_id", nullable = false, updatable = false)
    private UUID reviewerId;

    @Column(nullable = false, updatable = false, length = 20)
    private String decision;

    @Column(name = "reason_or_comment", updatable = false, columnDefinition = "text")
    private String feedback;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected ReviewDecisionRecord() {}

    public ReviewDecisionRecord(UUID ticketId, UUID reviewerId, String decision, String feedback, Instant decidedAt) {
        this.ticketId = ticketId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.feedback = feedback;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }
}
