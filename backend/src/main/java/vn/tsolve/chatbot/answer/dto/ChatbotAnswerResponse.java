package vn.tsolve.chatbot.answer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public record ChatbotAnswerResponse(
        UUID answerId,
        Outcome outcome,
        String text,
        List<ChatbotCitation> citations,
        // Absent for the Jira App and inside a stored turn (AC-013.1).
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID chatSessionId) {
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
