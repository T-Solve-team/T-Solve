package vn.tsolve.ticket.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.entity.ImportBatch;
import vn.tsolve.ticket.entity.ImportPreview;
import vn.tsolve.ticket.repository.ImportBatchRepository;

/**
 * Records a Failed Import Batch in its own transaction, so the rollback that guarantees "nothing imported" does not
 * erase the record of the failure (design B1).
 */
@Component
public class ImportBatchRecorder {

    public record Counts(long errorRows, long removedRows) {}

    private final ImportBatchRepository batches;

    public ImportBatchRecorder(ImportBatchRepository batches) {
        this.batches = batches;
    }

    /** error = exception type + MSG code only; never row content (backend/AGENTS.md §9). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(ImportPreview preview, Counts counts, String error) {
        batches.save(ImportBatch.failed(
                preview.getProjectId(),
                preview.getOwner(),
                preview.getSource(),
                preview.getFileName(),
                (int) counts.errorRows(),
                (int) counts.removedRows(),
                error));
    }
}
