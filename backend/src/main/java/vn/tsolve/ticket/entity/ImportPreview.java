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

/** An uploaded file waiting for Confirm. Each Project Manager has at most one (unique owner). */
@Entity
@Table(name = "import_preview")
public class ImportPreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String owner;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TicketSource source;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportPreview() {}

    public ImportPreview(String owner, Long projectId, TicketSource source, String fileName, Instant createdAt) {
        this.owner = owner;
        this.projectId = projectId;
        this.source = source;
        this.fileName = fileName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public Long getProjectId() {
        return projectId;
    }

    public TicketSource getSource() {
        return source;
    }

    public String getFileName() {
        return fileName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
