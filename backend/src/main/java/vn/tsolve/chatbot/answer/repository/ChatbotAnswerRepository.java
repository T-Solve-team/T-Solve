package vn.tsolve.chatbot.answer.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;

public interface ChatbotAnswerRepository extends JpaRepository<ChatbotAnswer, UUID> {
    Optional<ChatbotAnswer> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ChatbotAnswer a WHERE a.id = :id AND a.userId = :userId")
    Optional<ChatbotAnswer> findForUpdateByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // BUG-326: only an ANSWERED or NO_SOURCE answer can be rated (AC-003.1); a refused or greeting row matches nothing.
    @Modifying
    @Query("UPDATE ChatbotAnswer a SET a.rating = :rating, a.ratedAt = :ratedAt "
            + "WHERE a.id = :id AND a.userId = :userId AND a.outcome IN ('ANSWERED', 'NO_SOURCE')")
    int updateRating(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("rating") ChatbotRating rating,
            @Param("ratedAt") Instant ratedAt);

    // AC-012.7: only a question asked in Jira, in that Project; BUG-326: only a searched answer.
    @Modifying
    @Query("UPDATE ChatbotAnswer a SET a.rating = :rating, a.ratedAt = :ratedAt "
            + "WHERE a.id = :id AND a.source = 'JIRA_APP' AND a.projectId = :projectId "
            + "AND a.outcome IN ('ANSWERED', 'NO_SOURCE')")
    int updateProjectRating(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("rating") ChatbotRating rating,
            @Param("ratedAt") Instant ratedAt);
}
