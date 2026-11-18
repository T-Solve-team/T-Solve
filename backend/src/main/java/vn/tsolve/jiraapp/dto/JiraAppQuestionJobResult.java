package vn.tsolve.jiraapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;

/** {@code code}: the refusal (MSG08 …) of a FAILED job; none for a timeout or an AI failure, which are MSG06. */
public record JiraAppQuestionJobResult(
        JiraAppJobStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) ChatbotAnswerResponse answer,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code) {

    public JiraAppQuestionJobResult(JiraAppJobStatus status, ChatbotAnswerResponse answer) {
        this(status, answer, null);
    }
}
