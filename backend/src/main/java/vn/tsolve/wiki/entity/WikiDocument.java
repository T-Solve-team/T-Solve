package vn.tsolve.wiki.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wiki_document")
public class WikiDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wiki_document_id")
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private WikiDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "file_key", nullable = false, length = 255)
    private String fileKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", nullable = false, length = 20)
    private IndexStatus indexStatus;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WikiDocument() {}

    public WikiDocument(
            long spaceId,
            UUID folderId,
            String title,
            Visibility visibility,
            WikiDocumentType documentType,
            String content,
            String fileKey,
            String fileName,
            long fileSize,
            String contentType,
            int wordCount,
            UUID ownerId,
            Instant now) {
        this.spaceId = spaceId;
        this.folderId = folderId;
        this.title = title;
        this.visibility = visibility;
        this.documentType = documentType;
        this.status = Status.PUBLISHED;
        this.content = content;
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.wordCount = wordCount;
        this.indexStatus = IndexStatus.PENDING;
        this.ownerId = ownerId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void trash(UUID actor, Instant now) {
        status = Status.DELETED;
        deletedBy = actor;
        deletedAt = now;
        updatedAt = now;
    }

    public void restore(String restoredTitle, Instant now) {
        status = Status.PUBLISHED;
        title = restoredTitle;
        deletedBy = null;
        deletedAt = null;
        updatedAt = now;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public String getTitle() {
        return title;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public WikiDocumentType getDocumentType() {
        return documentType;
    }

    public Status getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public int getWordCount() {
        return wordCount;
    }

    public IndexStatus getIndexStatus() {
        return indexStatus;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum Visibility {
        DEPARTMENT,
        COMPANY_WIDE
    }

    public enum Status {
        PUBLISHED,
        DELETED
    }

    public enum IndexStatus {
        PENDING,
        INDEXED,
        FAILED
    }
}
