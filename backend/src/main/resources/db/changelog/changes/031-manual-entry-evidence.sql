--liquibase formatted sql

--changeset ticket:031-manual-entry-evidence
ALTER TABLE evidence ADD COLUMN storage_key VARCHAR(100);

CREATE TABLE import_preview_evidence (
    import_preview_evidence_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    preview_id                 BIGINT       NOT NULL REFERENCES import_preview (id) ON DELETE CASCADE,
    file_name                  VARCHAR(255) NOT NULL,
    file_type                  VARCHAR(100) NOT NULL,
    file_size                  BIGINT       NOT NULL,
    storage_key                VARCHAR(100) NOT NULL,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_preview_evidence_size CHECK (file_size >= 0)
);
CREATE INDEX idx_import_preview_evidence_preview ON import_preview_evidence (preview_id);

--rollback DROP TABLE import_preview_evidence;
--rollback ALTER TABLE evidence DROP COLUMN storage_key;
