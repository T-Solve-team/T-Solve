package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;

/** The {@code ChatbotQuestion} of the contract, with the Jira App's limit of 10 earlier turns (AC-010.3). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraAppQuestionRequest(
        @NotBlank(message = "MSG01") @Size(max = 1000, message = "MSG02") String question,
        @Size(max = 10, message = "MSG02") List<ChatbotTurnRequest> history) {

    public ChatbotQuestionRequest toChatbotQuestion() {
        return new ChatbotQuestionRequest(question, history == null ? List.of() : history);
    }
}
