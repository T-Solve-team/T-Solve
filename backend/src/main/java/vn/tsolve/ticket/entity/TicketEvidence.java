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

@Entity
@Immutable
@Table(name = "evidence")
public class TicketEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "evidence_id")
    private UUID id;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_size")
    private long fileSize;

    @Column(name = "upload_date", nullable = false, updatable = false)
    private Instant uploadDate = Instant.now();

    @Column(name = "storage_key")
    private String storageKey;

    protected TicketEvidence() {}

    public static TicketEvidence of(UUID ticketId, String fileName, String fileType, long fileSize, String storageKey) {
        TicketEvidence evidence = new TicketEvidence();
        evidence.ticketId = ticketId;
        evidence.fileName = fileName;
        evidence.fileType = fileType;
        evidence.fileSize = fileSize;
        evidence.storageKey = storageKey;
        return evidence;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
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

    public Instant getUploadDate() {
        return uploadDate;
    }

    public String getStorageKey() {
        return storageKey;
    }
}
