package vn.tsolve.chatbot.conversation.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationSummaryResponse;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotConversationService {
    List<ChatbotConversationSummaryResponse> list(TSolveUser user);

    ChatbotConversationResponse get(TSolveUser user, String chatSessionId);

    void delete(TSolveUser user, String chatSessionId);

    List<UUID> findExpiredIds(Instant cutoff);

    boolean deleteExpired(UUID chatSessionId, Instant cutoff);
}
