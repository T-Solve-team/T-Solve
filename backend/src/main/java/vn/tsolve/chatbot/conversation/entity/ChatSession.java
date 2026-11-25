package vn.tsolve.chatbot.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** A web user's conversation (spec STORY-013); its times come from the database. */
@Entity
@Table(name = "chat_session")
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chat_session_id")
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String title;

    @Generated(event = EventType.INSERT)
    @Column(name = "started_at", nullable = false, insertable = false, updatable = false)
    private Instant startedAt;

    // Moved by ChatSessionRepository.touch, never through the entity.
    @Generated(event = EventType.INSERT)
    @Column(name = "last_message_at", nullable = false, insertable = false, updatable = false)
    private Instant lastMessageAt;

    protected ChatSession() {}

    public ChatSession(UUID userId, String title) {
        this.userId = userId;
        this.title = title;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
