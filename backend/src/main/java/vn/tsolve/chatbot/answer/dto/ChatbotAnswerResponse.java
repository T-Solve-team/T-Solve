package vn.tsolve.chatbot.answer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * {@code chatSessionId} is absent for the Jira App and inside a stored turn (AC-013.1); {@code title} names a new
 * conversation (AC-009.9). Both are null, and left out of the JSON, when they do not apply.
 */
public record ChatbotAnswerResponse(
        UUID answerId,
        Outcome outcome,
        String text,
        List<ChatbotCitation> citations,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID chatSessionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title) {
    /** An answer with no conversation and no title. */
    public ChatbotAnswerResponse(UUID answerId, Outcome outcome, String text, List<ChatbotCitation> citations) {
        this(answerId, outcome, text, citations, null, null);
    }

    public enum Outcome {
        ANSWERED,
        NO_SOURCE,
        GREETING,
        OFF_TOPIC,
        INJECTION
    }
}
