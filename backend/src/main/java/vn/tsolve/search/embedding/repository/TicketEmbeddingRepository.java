package vn.tsolve.search.embedding.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.search.embedding.entity.TicketEmbedding;

public interface TicketEmbeddingRepository extends JpaRepository<TicketEmbedding, UUID> {
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO ticket_embedding (ticket_id, embedding, model)
                    VALUES (:ticketId, CAST(:vector AS vector), :model)
                    ON CONFLICT (ticket_id) DO UPDATE
                    SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, updated_at = now()
                    """,
            nativeQuery = true)
    void upsert(@Param("ticketId") UUID ticketId, @Param("vector") String vector, @Param("model") String model);

    @Transactional(readOnly = true)
    @Query(
            value =
                    """
                    SELECT t.ticket_id FROM ticket t
                    WHERE t.status = 'PUBLISHED'
                      AND NOT EXISTS (SELECT 1 FROM ticket_embedding e WHERE e.ticket_id = t.ticket_id)
                      AND t.ticket_id <> ALL(CAST(:skipped AS uuid[]))
                    ORDER BY t.ticket_id LIMIT :limit
                    """,
            nativeQuery = true)
    List<UUID> findPublishedWithoutEmbedding(@Param("limit") int limit, @Param("skipped") UUID[] skipped);
}
