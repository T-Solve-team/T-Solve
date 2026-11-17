package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;

public record JiraAppQuestionJobResult(
        Status status,
        @JsonInclude(JsonInclude.Include.NON_NULL) ChatbotAnswerResponse answer) {
    public enum Status {
        PENDING,
        DONE,
        FAILED
    }
}
