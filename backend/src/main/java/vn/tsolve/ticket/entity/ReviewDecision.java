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
import vn.tsolve.ticket.dto.ReviewActor;

@Entity
@Table(name = "ticket_review_decision")
public class ReviewDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "writer_id", nullable = false)
    private String writerId;

    @Column(name = "writer_name", nullable = false)
    private String writerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewAction action;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ReviewDecision() {}

    public ReviewDecision(ReviewTicket ticket, ReviewActor actor, ReviewAction action, String comment, Instant at) {
        ticketId = ticket.getId();
        actorId = actor.id();
        actorName = actor.name();
        writerId = ticket.getWriterId();
        writerName = ticket.getAuthorName();
        this.action = action;
        this.comment = action == ReviewAction.APPROVE ? null : comment;
        decidedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getWriterId() {
        return writerId;
    }

    public String getWriterName() {
        return writerName;
    }

    public ReviewAction getAction() {
        return action;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
