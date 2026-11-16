package vn.tsolve.wiki.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The embedding is read and written through native pgvector queries, as for ticket_embedding. */
@Entity
@Table(name = "wiki_chunk")
public class WikiChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wiki_chunk_id")
    private UUID id;

    @Column(name = "wiki_document_id", nullable = false)
    private UUID wikiDocumentId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WikiChunk() {}

    public UUID getId() {
        return id;
    }

    public UUID getWikiDocumentId() {
        return wikiDocumentId;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
