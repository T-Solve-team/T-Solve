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
import vn.tsolve.ticket.dto.ReviewAction;

@Entity
@Table(name = "ticket_review_notification")
public class ReviewNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_id", nullable = false, unique = true)
    private Long decisionId;

    @Column(name = "recipient_id", nullable = false)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewAction action;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected ReviewNotification() {}

    public ReviewNotification(ReviewDecision decision) {
        if (decision.getAction() == ReviewAction.APPROVE) {
            throw new IllegalArgumentException("Approval does not send a ticket notification");
        }
        decisionId = decision.getId();
        recipientId = decision.getWriterId();
        action = decision.getAction();
        comment = decision.getComment();
        createdAt = decision.getDecidedAt();
    }

    public void acknowledgeDelivery(Instant at) {
        if (deliveredAt == null) deliveredAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public ReviewAction getAction() {
        return action;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
