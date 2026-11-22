package vn.tsolve.wiki.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.tsolve.wiki.entity.WikiDocument;

public interface WikiDocumentRepository extends JpaRepository<WikiDocument, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WikiDocument d where d.id = :documentId")
    Optional<WikiDocument> findForIndexing(@Param("documentId") UUID documentId);

    @Modifying
    @Query("update WikiDocument d set d.indexStatus = :indexStatus where d.id = :documentId")
    void updateIndexStatus(
            @Param("documentId") UUID documentId, @Param("indexStatus") WikiDocument.IndexStatus indexStatus);

    long countByFolderId(UUID folderId);

    /** Folds case with {@code lower()}, like {@code uq_wiki_document_published_title}, not Spring's {@code upper()}. */
    @Query("select count(d) > 0 from WikiDocument d where d.spaceId = :spaceId and d.status = :status"
            + " and (d.folderId = :folderId or (:folderId is null and d.folderId is null))"
            + " and lower(d.title) = lower(:title)")
    boolean existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(
            @Param("spaceId") Long spaceId,
            @Param("folderId") UUID folderId,
            @Param("status") WikiDocument.Status status,
            @Param("title") String title);
}
