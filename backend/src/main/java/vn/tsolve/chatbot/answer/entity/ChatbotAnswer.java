package vn.tsolve.chatbot.answer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;

// @Immutable breaks @Generated insert columns in Hibernate 6; columns use updatable = false instead.
@Entity
@Table(name = "chatbot_answer")
public class ChatbotAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chatbot_answer_id")
    private UUID id;

    // Null for a question asked on the Jira App's Ask T-Solve page, which has no T-Solve user (AC-012.2).
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 10)
    private String source;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, updatable = false, length = 20)
    private String outcome;

    @Column(name = "answer_text", nullable = false, updatable = false, columnDefinition = "text")
    private String answerText;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ChatbotRating rating;

    @Column(name = "rated_at")
    private Instant ratedAt;

    @Column(name = "jira_issue_key", length = 255)
    private String jiraIssueKey;

    // AC-014.1 to AC-014.3: ids and scores of the kept sources, never returned to a client.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<AnswerSource> sources = List.of();

    protected ChatbotAnswer() {}

    public static final String WEB = "WEB";
    public static final String JIRA_APP = "JIRA_APP";

    public ChatbotAnswer(UUID userId, String question, String outcome, String answerText) {
        this(userId, WEB, null, question, outcome, answerText);
    }

    private ChatbotAnswer(
            UUID userId, String source, UUID projectId, String question, String outcome, String answerText) {
        this.userId = userId;
        this.source = source;
        this.projectId = projectId;
        this.question = question;
        this.outcome = outcome;
        this.answerText = answerText;
    }

    /** A question asked on the Jira App's Ask T-Solve page: no user, the Project it was asked in (AC-012.2). */
    public static ChatbotAnswer forProject(UUID projectId, String question, String outcome, String answerText) {
        return new ChatbotAnswer(null, JIRA_APP, projectId, question, outcome, answerText);
    }

    public String getSource() {
        return source;
    }

    public UUID getProjectId() {
        return projectId;
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

    public ChatbotRating getRating() {
        return rating;
    }

    public Instant getRatedAt() {
        return ratedAt;
    }

    public String getJiraIssueKey() {
        return jiraIssueKey;
    }

    public List<AnswerSource> getSources() {
        return sources;
    }

    /** Set before the row is saved; the column is not updatable. */
    public ChatbotAnswer withSources(List<AnswerSource> kept) {
        sources = List.copyOf(kept);
        return this;
    }

    public void recordJiraIssue(String issueKey) {
        jiraIssueKey = issueKey;
    }
}
