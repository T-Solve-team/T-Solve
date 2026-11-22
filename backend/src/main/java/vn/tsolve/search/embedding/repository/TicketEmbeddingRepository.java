package vn.tsolve.search.embedding.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.search.embedding.entity.TicketEmbedding;

/** text_version 2 = EmbeddingText with the description (search AC-005.4, AC-005.10). */
public interface TicketEmbeddingRepository extends JpaRepository<TicketEmbedding, UUID> {
    @Modifying
    @Query(value = """
                    INSERT INTO ticket_embedding (ticket_id, embedding, model, text_version)
                    VALUES (:ticketId, CAST(:vector AS vector), :model, 2)
                    ON CONFLICT (ticket_id) DO UPDATE
                    SET embedding = EXCLUDED.embedding, model = EXCLUDED.model,
                        text_version = EXCLUDED.text_version, updated_at = now()
                    """, nativeQuery = true)
    void upsert(@Param("ticketId") UUID ticketId, @Param("vector") String vector, @Param("model") String model);

    @Transactional(readOnly = true)
    @Query(value = """
                    SELECT t.ticket_id FROM ticket t
                    LEFT JOIN ticket_embedding e ON e.ticket_id = t.ticket_id
                    WHERE t.status = 'PUBLISHED'
                      AND (e.ticket_id IS NULL OR e.text_version < 2)
                      AND t.ticket_id <> ALL(CAST(:skipped AS uuid[]))
                    ORDER BY e.ticket_id IS NOT NULL, t.ticket_id LIMIT :limit
                    """, nativeQuery = true)
    List<UUID> findPublishedWithoutEmbedding(@Param("limit") int limit, @Param("skipped") UUID[] skipped);
}
