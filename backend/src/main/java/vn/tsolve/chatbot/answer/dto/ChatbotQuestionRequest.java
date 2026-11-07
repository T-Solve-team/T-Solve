package vn.tsolve.chatbot.answer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatbotQuestionRequest(
        @NotBlank(message = "MSG01") @Size(max = 1000, message = "MSG02") String question,
        List<ChatbotTurnRequest> history) {}
