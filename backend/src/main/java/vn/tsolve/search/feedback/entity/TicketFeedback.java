package vn.tsolve.search.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Feedback is written by the repository's atomic upsert. */
@Entity
@Table(name = "ticket_feedback")
public class TicketFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_feedback_id")
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String rating;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketFeedback() {}

    public UUID getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
