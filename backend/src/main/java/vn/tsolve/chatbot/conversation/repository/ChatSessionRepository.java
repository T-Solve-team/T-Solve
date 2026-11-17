package vn.tsolve.chatbot.conversation.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.chatbot.conversation.entity.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

    /** AC-013.3: the conversation's last message is now (the transaction's time, as the message's created_at). */
    @Modifying
    @Query("UPDATE ChatSession s SET s.lastMessageAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    int touch(@Param("id") UUID id);
}
