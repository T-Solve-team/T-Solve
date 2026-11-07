package vn.tsolve.search.embedding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The vector is read and written only by native pgvector queries. */
@Entity
@Table(name = "ticket_embedding")
public class TicketEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_embedding_id")
    private UUID id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketEmbedding() {}
}
