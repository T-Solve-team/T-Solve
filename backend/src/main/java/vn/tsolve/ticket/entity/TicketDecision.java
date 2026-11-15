package vn.tsolve.ticket.entity;

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

/**
 * A permanent decision on a Ticket: a review decision carries the reviewer's user id, the approve decision of an
 * import or a manual entry the Project Manager's sign-in name (KAN-137).
 */
@Entity
@Table(name = "ticket_decision")
public class TicketDecision {

    public enum Decision {
        APPROVED,
        REJECTED,
        CHANGES_REQUESTED,
        SEND_BACK,
        UNPUBLISH,
        REPUBLISH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_decision_id")
    private UUID id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "reviewer_id", updatable = false)
    private UUID reviewerId;

    @Column(updatable = false, length = 150)
    private String reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Decision decision;

    @Column(updatable = false, columnDefinition = "text")
    private String feedback;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected TicketDecision() {}

    public TicketDecision(UUID ticketId, UUID reviewerId, String decision, String feedback, Instant decidedAt) {
        this.ticketId = ticketId;
        this.reviewerId = reviewerId;
        this.decision = Decision.valueOf(decision);
        this.feedback = feedback;
        this.decidedAt = decidedAt;
    }

    /** The approve decision of a Ticket published by an import or a manual entry (GBR-23). */
    public static TicketDecision approvedByImport(UUID ticketId, String reviewer) {
        TicketDecision decision = new TicketDecision();
        decision.ticketId = ticketId;
        decision.reviewer = reviewer;
        decision.decision = Decision.APPROVED;
        decision.decidedAt = Instant.now();
        return decision;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public String getReviewer() {
        return reviewer;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getFeedback() {
        return feedback;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
