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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/** One question and answer of a conversation, as the user saw it (AC-013.3). */
@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chat_message_id")
    private UUID id;

    @Column(name = "chat_session_id", nullable = false, updatable = false)
    private UUID chatSessionId;

    @Column(name = "chatbot_answer_id", nullable = false, updatable = false)
    private UUID chatbotAnswerId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, updatable = false, length = 20)
    private String outcome;

    @Column(name = "answer_text", nullable = false, updatable = false, columnDefinition = "text")
    private String answerText;

    // The ChatbotCitation[] JSON exactly as returned to the user.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String citations;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {}

    public ChatMessage(
            UUID chatSessionId,
            UUID chatbotAnswerId,
            String question,
            String outcome,
            String answerText,
            String citations) {
        this.chatSessionId = chatSessionId;
        this.chatbotAnswerId = chatbotAnswerId;
        this.question = question;
        this.outcome = outcome;
        this.answerText = answerText;
        this.citations = citations;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChatSessionId() {
        return chatSessionId;
    }

    public UUID getChatbotAnswerId() {
        return chatbotAnswerId;
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

    public String getCitations() {
        return citations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
