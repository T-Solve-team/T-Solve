package vn.tsolve.chatbot.issue.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatbotIssueDraftRequest(List<ChatbotTurnRequest> history) {
    public ChatbotIssueDraftRequest {
        ChatbotQuestionRequest.validateHistory(history);
    }
}
