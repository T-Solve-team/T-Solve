package vn.tsolve.wiki.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.wiki.entity.WikiChunk;

public interface WikiChunkRepository extends JpaRepository<WikiChunk, UUID> {
    @Modifying
    @Query("delete from WikiChunk c where c.wikiDocumentId = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    @Modifying
    @Query(value = """
            INSERT INTO wiki_chunk (wiki_document_id, position, text, embedding, model, created_at)
            VALUES (:documentId, :position, :text, CAST(:vector AS vector), :model, now())
            """, nativeQuery = true)
    void insert(
            @Param("documentId") UUID documentId,
            @Param("position") int position,
            @Param("text") String text,
            @Param("vector") String vector,
            @Param("model") String model);
}
