package vn.tsolve.ticket.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.ImportPreviewEvidence;

public interface ImportPreviewEvidenceRepository extends JpaRepository<ImportPreviewEvidence, UUID> {

    List<ImportPreviewEvidence> findByPreviewIdOrderByCreatedAt(Long previewId);

    void deleteByPreviewId(Long previewId);
}
