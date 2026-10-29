package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One import (UC-21): recorded once, Completed or Failed, never updated or deleted. */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "performed_by", nullable = false, updatable = false)
    private String performedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TicketSource source;

    @Column(name = "file_name", updatable = false)
    private String fileName;

    @Column(name = "published_count", nullable = false, updatable = false)
    private int publishedCount;

    @Column(name = "error_count", nullable = false, updatable = false)
    private int errorCount;

    @Column(name = "removed_count", nullable = false, updatable = false)
    private int removedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ImportBatchStatus status;

    @Column(updatable = false, columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ImportBatch() {}

    private ImportBatch(
            Long projectId,
            String performedBy,
            TicketSource source,
            String fileName,
            int publishedCount,
            int errorCount,
            int removedCount,
            ImportBatchStatus status,
            String error) {
        this.projectId = projectId;
        this.performedBy = performedBy;
        this.source = source;
        this.fileName = fileName;
        this.publishedCount = publishedCount;
        this.errorCount = errorCount;
        this.removedCount = removedCount;
        this.status = status;
        this.error = error;
    }

    public static ImportBatch completed(
            Long projectId,
            String performedBy,
            TicketSource source,
            String fileName,
            int publishedCount,
            int errorCount,
            int removedCount) {
        return new ImportBatch(
                projectId,
                performedBy,
                source,
                fileName,
                publishedCount,
                errorCount,
                removedCount,
                ImportBatchStatus.COMPLETED,
                null);
    }

    public static ImportBatch failed(
            Long projectId,
            String performedBy,
            TicketSource source,
            String fileName,
            int errorCount,
            int removedCount,
            String error) {
        return new ImportBatch(
                projectId, performedBy, source, fileName, 0, errorCount, removedCount, ImportBatchStatus.FAILED, error);
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public TicketSource getSource() {
        return source;
    }

    public String getFileName() {
        return fileName;
    }

    public int getPublishedCount() {
        return publishedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getRemovedCount() {
        return removedCount;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
