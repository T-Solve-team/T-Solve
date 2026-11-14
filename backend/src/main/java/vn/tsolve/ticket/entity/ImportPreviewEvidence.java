package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Files of a Manual Draft are inserted or deleted, never updated (AC-006.18, AC-006.19). */
@Entity
@Immutable
@Table(name = "import_preview_evidence")
public class ImportPreviewEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "import_preview_evidence_id")
    private UUID id;

    @Column(name = "preview_id", nullable = false, updatable = false)
    private Long previewId;

    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = false, updatable = false, length = 100)
    private String fileType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 100)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ImportPreviewEvidence() {}

    public static ImportPreviewEvidence of(
            Long previewId, String fileName, String fileType, long fileSize, String storageKey) {
        ImportPreviewEvidence evidence = new ImportPreviewEvidence();
        evidence.previewId = previewId;
        evidence.fileName = fileName;
        evidence.fileType = fileType;
        evidence.fileSize = fileSize;
        evidence.storageKey = storageKey;
        return evidence;
    }

    public UUID getId() {
        return id;
    }

    public Long getPreviewId() {
        return previewId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
