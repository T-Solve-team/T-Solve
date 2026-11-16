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

    boolean existsBySpaceIdAndFolderIdAndStatusAndTitleIgnoreCase(
            Long spaceId, UUID folderId, WikiDocument.Status status, String title);
}
