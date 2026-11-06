--liquibase formatted sql

--changeset search:010-search-ticket-embedding
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ticket_embedding (
    ticket_embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id BIGINT NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
    embedding vector(1024) NOT NULL,
    model VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ticket_embedding_ticket UNIQUE (ticket_id)
);

--rollback DROP TABLE ticket_embedding;
