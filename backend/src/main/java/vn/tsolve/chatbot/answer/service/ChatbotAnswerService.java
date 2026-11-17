package vn.tsolve.chatbot.answer.service;

import java.util.UUID;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotAnswerService {
    ChatbotAnswerResponse ask(TSolveUser user, ChatbotQuestionRequest request);

    /**
     * Answers a question asked in Jira for the Active Project with this public id, from its Department's Solutions and
     * Wiki, and stores it without a user (STORY-012); 403 MSG08 for an unknown or inactive Project.
     */
    ChatbotAnswerResponse askForProject(UUID projectId, ChatbotQuestionRequest request);

    /** Rates a question asked in Jira in this Project (AC-012.7); 404 MSG04 for any other answer. */
    void rateForProject(UUID projectId, UUID answerId, ChatbotRating rating);
}
