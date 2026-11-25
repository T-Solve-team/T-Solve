package vn.tsolve.chatbot.answer.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotAskRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotAnswerService {
    /**
     * Answers a web question in the user's conversation {@code chatSessionId}, or in a new one (STORY-013); 404 MSG04
     * for an unknown conversation, before any AI call.
     */
    ChatbotAnswerResponse ask(TSolveUser user, ChatbotAskRequest request);

    /**
     * Answers a question asked in Jira for the Active Project with this public id, from its Department's Solutions and
     * the Wiki the asker may read: what {@code reader} (the linked T-Solve user) may view, or Company-wide documents
     * when empty (AC-012.1). Stored without a user (AC-012.2); 403 MSG08 for an unknown or inactive Project.
     */
    ChatbotAnswerResponse askForProject(UUID projectId, Optional<TSolveUser> reader, ChatbotQuestionRequest request);

    /** Rates a question asked in Jira in this Project (AC-012.7); 404 MSG04 for any other answer. */
    void rateForProject(UUID projectId, UUID answerId, ChatbotRating rating);
}
