package vn.tsolve.chatbot.answer.dto;

import java.util.List;
import java.util.UUID;

public record ChatbotAnswerResponse(UUID answerId, Outcome outcome, String text, List<ChatbotCitation> citations) {
    public enum Outcome {
        ANSWERED,
        NO_SOURCE
    }
}
