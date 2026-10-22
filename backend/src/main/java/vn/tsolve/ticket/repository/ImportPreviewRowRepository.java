package vn.tsolve.ticket.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.ImportPreviewRow;
import vn.tsolve.ticket.entity.PreviewRowStatus;

public interface ImportPreviewRowRepository extends JpaRepository<ImportPreviewRow, ImportPreviewRow.Id> {

    Page<ImportPreviewRow> findByIdPreviewIdOrderByIdRowNo(Long previewId, Pageable pageable);

    Page<ImportPreviewRow> findByIdPreviewIdAndStatusOrderByIdRowNo(
            Long previewId, PreviewRowStatus status, Pageable pageable);

    List<ImportPreviewRow> findByIdPreviewIdAndStatus(Long previewId, PreviewRowStatus status);

    long countByIdPreviewIdAndStatus(Long previewId, PreviewRowStatus status);
}
