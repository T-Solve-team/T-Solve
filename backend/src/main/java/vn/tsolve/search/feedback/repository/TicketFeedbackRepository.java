package vn.tsolve.search.feedback.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.search.feedback.entity.TicketFeedback;

public interface TicketFeedbackRepository extends JpaRepository<TicketFeedback, UUID> {
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO ticket_feedback (ticket_id, user_id, rating, comment, channel)
                    VALUES (:ticketId, :userId, :rating, :comment, :channel)
                    ON CONFLICT (ticket_id, user_id) DO UPDATE
                    SET rating = EXCLUDED.rating, comment = EXCLUDED.comment,
                        channel = EXCLUDED.channel, updated_at = now()
                    """,
            nativeQuery = true)
    void upsert(
            @Param("ticketId") UUID ticketId,
            @Param("userId") UUID userId,
            @Param("rating") String rating,
            @Param("comment") String comment,
            @Param("channel") String channel);
}
