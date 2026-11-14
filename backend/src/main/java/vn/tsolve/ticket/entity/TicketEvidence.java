package vn.tsolve.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "evidence")
public class TicketEvidence {

    @Id
    @Column(name = "evidence_id")
    private UUID id;

    @Column(name = "ticket_id")
    private UUID ticketId;

    /** The file's key in the Workspace file storage; null only on rows from before manual entry (KAN-133). */
    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "file_name")
    private String fileName;

    /** The lower-cased file-name extension, e.g. {@code pdf} (spec D18). */
    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_size")
    private long fileSize;

    protected TicketEvidence() {}

    /** The id is given here, not by the database, so a Draft's Evidence keeps its id when it becomes a Ticket's. */
    public static TicketEvidence of(UUID ticketId, String storageKey, String fileName, String fileType, long fileSize) {
        TicketEvidence evidence = new TicketEvidence();
        evidence.id = UUID.randomUUID();
        evidence.ticketId = ticketId;
        evidence.storageKey = storageKey;
        evidence.fileName = fileName;
        evidence.fileType = fileType;
        evidence.fileSize = fileSize;
        return evidence;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public String getStorageKey() {
        return storageKey;
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
}
