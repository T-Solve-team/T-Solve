package vn.tsolve.chatbot.answer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.generator.EventType;

@Entity
@Immutable
@Table(name = "chatbot_answer")
public class ChatbotAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chatbot_answer_id")
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, updatable = false, length = 20)
    private String outcome;

    @Column(name = "answer_text", nullable = false, updatable = false, columnDefinition = "text")
    private String answerText;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ChatbotAnswer() {}

    public ChatbotAnswer(UUID userId, String question, String outcome, String answerText) {
        this.userId = userId;
        this.question = question;
        this.outcome = outcome;
        this.answerText = answerText;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getAnswerText() {
        return answerText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
