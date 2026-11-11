package vn.tsolve.chatbot.answer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import vn.tsolve.chatbot.exception.InvalidChatbotRequestException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatbotQuestionRequest(
        @NotBlank(message = "MSG01") @Size(max = 1000, message = "MSG02") String question,
        List<ChatbotTurnRequest> history) {
    public ChatbotQuestionRequest {
        if (history == null || history.size() > 10) {
            throw new InvalidChatbotRequestException();
        }
        for (ChatbotTurnRequest turn : history) {
            if (turn == null
                    || turn.question() == null
                    || turn.answer() == null
                    || turn.question().length() > 1000
                    || turn.answer().length() > 10000) {
                throw new InvalidChatbotRequestException();
            }
        }
    }
}
