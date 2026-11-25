package vn.tsolve.chatbot.conversation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.chatbot.conversation.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findTop10ByChatSessionIdOrderByCreatedAtDesc(UUID chatSessionId);

    List<ChatMessage> findTop10ByChatSessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID chatSessionId, Instant before);

    Optional<ChatMessage> findByChatbotAnswerId(UUID chatbotAnswerId);

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId);

    @Query("SELECT m.chatbotAnswerId FROM ChatMessage m WHERE m.chatSessionId = :chatSessionId")
    List<UUID> findAnswerIdsByChatSessionId(@Param("chatSessionId") UUID chatSessionId);
}
