package vn.tsolve.chatbot.answer.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;

public interface ChatbotAnswerRepository extends JpaRepository<ChatbotAnswer, UUID> {
    @Modifying
    @Query(
            "UPDATE ChatbotAnswer a SET a.rating = :rating, a.ratedAt = :ratedAt WHERE a.id = :id AND a.userId = :userId")
    int updateRating(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("rating") ChatbotRating rating,
            @Param("ratedAt") Instant ratedAt);
}
