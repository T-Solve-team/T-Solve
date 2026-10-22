package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** A review decision on a ticket; review history is permanent, so it is never updated. */
@Entity
@Table(name = "ticket_review_decision")
public class TicketReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_review_decision_seq")
    @SequenceGenerator(
            name = "ticket_review_decision_seq",
            sequenceName = "ticket_review_decision_id_seq",
            allocationSize = 50)
    private Long id;

    // id of the ticket row (ticket.id), not the business Ticket ID
    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Column(nullable = false, updatable = false)
    private String reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReviewDecision decision;

    @Column(updatable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt = Instant.now();

    protected TicketReviewDecision() {}

    private TicketReviewDecision(Long ticketId, String reviewer, ReviewDecision decision) {
        this.ticketId = ticketId;
        this.reviewer = reviewer;
        this.decision = decision;
    }

    public static TicketReviewDecision approve(Long ticketId, String reviewer) {
        return new TicketReviewDecision(ticketId, reviewer, ReviewDecision.APPROVE);
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getReviewer() {
        return reviewer;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
