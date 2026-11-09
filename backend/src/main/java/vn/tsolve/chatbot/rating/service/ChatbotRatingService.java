package vn.tsolve.chatbot.rating.service;

import vn.tsolve.chatbot.rating.dto.ChatbotRatingRequest;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotRatingService {
    void rate(TSolveUser user, String answerId, ChatbotRatingRequest request);

    void remove(TSolveUser user, String answerId);
}
