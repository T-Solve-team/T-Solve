package vn.tsolve.chatbot.conversation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.chatbot.conversation.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findTop10ByChatSessionIdOrderByCreatedAtDesc(UUID chatSessionId);

    List<ChatMessage> findTop10ByChatSessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID chatSessionId, Instant before);

    Optional<ChatMessage> findByChatbotAnswerId(UUID chatbotAnswerId);
}
