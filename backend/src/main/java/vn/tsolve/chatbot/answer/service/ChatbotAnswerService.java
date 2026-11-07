package vn.tsolve.chatbot.answer.service;

import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotAnswerService {
    ChatbotAnswerResponse ask(TSolveUser user, ChatbotQuestionRequest request);
}
