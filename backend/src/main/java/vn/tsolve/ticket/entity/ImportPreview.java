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
import java.time.LocalDate;
import java.util.UUID;

/**
 * An uploaded file or a Jira pull waiting for Confirm. Each Project Manager has at most one that is not a Draft
 * (partial unique index on owner); Drafts are kept until finished or deleted (GBR-26).
 */
@Entity
@Table(name = "import_preview")
public class ImportPreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String owner;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TicketSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, updatable = false)
    private ImportType importType;

    @Column(name = "file_name", updatable = false)
    private String fileName;

    @Column(name = "resolved_from", updatable = false)
    private LocalDate resolvedFrom;

    @Column(name = "resolved_to", updatable = false)
    private LocalDate resolvedTo;

    @Column(name = "more_available", nullable = false, updatable = false)
    private boolean moreAvailable;

    @Column(name = "draft_saved_at")
    private Instant draftSavedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportPreview() {}

    public ImportPreview(
            String owner,
            UUID projectId,
            TicketSource source,
            ImportType importType,
            String fileName,
            Instant createdAt) {
        this.owner = owner;
        this.projectId = projectId;
        this.source = source;
        this.importType = importType;
        this.fileName = fileName;
        this.createdAt = createdAt;
    }

    /**
     * A Jira pull: no file, and no Solutions, so it is handled like a {@code WITHOUT_SOLUTION} file; moreAvailable =
     * more issues match the range than were kept (MSG35).
     */
    public static ImportPreview jira(
            String owner, UUID projectId, LocalDate from, LocalDate to, boolean moreAvailable, Instant createdAt) {
        ImportPreview preview = new ImportPreview(
                owner, projectId, TicketSource.JIRA_IMPORT, ImportType.WITHOUT_SOLUTION, null, createdAt);
        preview.resolvedFrom = from;
        preview.resolvedTo = to;
        preview.moreAvailable = moreAvailable;
        return preview;
    }

    /** Marks the preview a Draft, or moves the save time of a Draft saved before (AC-004.1, AC-004.7). */
    public void saveAsDraft(Instant savedAt) {
        draftSavedAt = savedAt;
    }

    public boolean isDraft() {
        return draftSavedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public TicketSource getSource() {
        return source;
    }

    public ImportType getImportType() {
        return importType;
    }

    public String getFileName() {
        return fileName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getResolvedFrom() {
        return resolvedFrom;
    }

    public LocalDate getResolvedTo() {
        return resolvedTo;
    }

    public boolean isMoreAvailable() {
        return moreAvailable;
    }

    public Instant getDraftSavedAt() {
        return draftSavedAt;
    }
}
