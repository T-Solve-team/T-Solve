package vn.tsolve.chatbot.conversation.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationResponse;
import vn.tsolve.chatbot.conversation.dto.ChatbotConversationSummaryResponse;
import vn.tsolve.common.security.TSolveUser;

public interface ChatbotConversationService {
    Page<ChatbotConversationSummaryResponse> list(TSolveUser user, Pageable page);

    ChatbotConversationResponse get(TSolveUser user, String chatSessionId);

    void delete(TSolveUser user, String chatSessionId);

    List<UUID> findExpiredIds(Instant cutoff);

    boolean deleteExpired(UUID chatSessionId, Instant cutoff);
}
