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

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private long fileSize;

    protected TicketEvidence() {}

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }
}
