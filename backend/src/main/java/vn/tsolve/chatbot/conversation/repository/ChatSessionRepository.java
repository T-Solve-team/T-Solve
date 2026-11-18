package vn.tsolve.chatbot.conversation.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.chatbot.conversation.entity.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

    Page<ChatSession> findByUserIdOrderByStartedAtDescIdDesc(UUID userId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ChatSession s WHERE s.id = :id AND s.userId = :userId")
    Optional<ChatSession> findForUpdateByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT s.id FROM ChatSession s WHERE s.lastMessageAt < :cutoff")
    List<UUID> findIdsLastMessageAtBefore(@Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ChatSession s WHERE s.id = :id AND s.lastMessageAt < :cutoff")
    Optional<ChatSession> findExpiredForUpdate(@Param("id") UUID id, @Param("cutoff") Instant cutoff);

    /** AC-013.3: the conversation's last message is now (the transaction's time, as the message's created_at). */
    @Modifying
    @Query("UPDATE ChatSession s SET s.lastMessageAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    int touch(@Param("id") UUID id);
}
