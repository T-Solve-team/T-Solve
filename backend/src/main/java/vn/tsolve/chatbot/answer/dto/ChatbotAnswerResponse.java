package vn.tsolve.chatbot.answer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/** {@code title} names a new conversation (AC-009.9); null, and left out of the JSON, for any other answer. */
public record ChatbotAnswerResponse(
        UUID answerId,
        Outcome outcome,
        String text,
        List<ChatbotCitation> citations,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title) {
    /** An answer without a conversation title. */
    public ChatbotAnswerResponse(UUID answerId, Outcome outcome, String text, List<ChatbotCitation> citations) {
        this(answerId, outcome, text, citations, null);
    }

    public enum Outcome {
        ANSWERED,
        NO_SOURCE,
        GREETING,
        OFF_TOPIC,
        INJECTION
    }
}
