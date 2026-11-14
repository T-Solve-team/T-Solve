package vn.tsolve.wiki.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.wiki.entity.WikiDocument;

public interface WikiDocumentRepository extends JpaRepository<WikiDocument, UUID> {
    long countByFolderId(UUID folderId);
}
