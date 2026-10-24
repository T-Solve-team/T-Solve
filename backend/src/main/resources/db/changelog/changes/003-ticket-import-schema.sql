--liquibase formatted sql

--changeset ticket:003-ticket-import-schema

-- Tickets with their Solution, Import Batches and the Import Preview (KAN-31, UC-21).
-- project_id has no FK yet: the project table belongs to the workspace module.

CREATE TABLE import_batch (                         -- never updated or deleted
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT       NOT NULL,
    performed_by     VARCHAR(150) NOT NULL,
    source           VARCHAR(20)  NOT NULL,         -- CSV (JIRA_IMPORT, MANUAL later)
    file_name        VARCHAR(255),
    published_count  INT          NOT NULL,
    error_count      INT          NOT NULL,
    removed_count    INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL,         -- COMPLETED, FAILED
    error            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_batch_project ON import_batch (project_id, created_at DESC);

CREATE TABLE ticket (
    id                BIGSERIAL PRIMARY KEY,
    ticket_id         VARCHAR(50)  NOT NULL,
    project_id        BIGINT       NOT NULL,
    source            VARCHAR(20)  NOT NULL,        -- SOLUTION_FORM, JIRA_IMPORT, CSV, MANUAL
    status            VARCHAR(20)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    writer            VARCHAR(100),
    resolved_date     DATE,
    symptoms          TEXT         NOT NULL,
    root_cause        TEXT         NOT NULL,
    resolution_steps  TEXT         NOT NULL,
    workaround        TEXT,
    scope             TEXT         NOT NULL,
    import_batch_id   BIGINT       REFERENCES import_batch (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ticket_ticket_id UNIQUE (ticket_id)
);
CREATE INDEX idx_ticket_project_status ON ticket (project_id, status);

CREATE TABLE ticket_import_decision (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT       NOT NULL REFERENCES ticket (id),
    reviewer    VARCHAR(150) NOT NULL,
    decision    VARCHAR(30)  NOT NULL,              -- APPROVE (REJECT, REQUEST_CHANGES later)
    reason      TEXT,
    decided_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_import_decision_ticket ON ticket_import_decision (ticket_id);

-- Hibernate allocates ids 50 at a time so inserts can be batched (design: Insert batching).
ALTER SEQUENCE ticket_id_seq INCREMENT BY 50;
ALTER SEQUENCE ticket_import_decision_id_seq INCREMENT BY 50;

CREATE TABLE import_preview (
    id          BIGSERIAL PRIMARY KEY,
    owner       VARCHAR(150) NOT NULL,
    project_id  BIGINT       NOT NULL,
    source      VARCHAR(20)  NOT NULL,              -- CSV
    file_name   VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_import_preview_owner ON import_preview (owner);   -- one preview per Project Manager
CREATE INDEX idx_import_preview_created ON import_preview (created_at);

-- Raw values as read from the file; limits apply only to ticket.
CREATE TABLE import_preview_row (
    preview_id        BIGINT      NOT NULL REFERENCES import_preview (id) ON DELETE CASCADE,
    row_no            INT         NOT NULL,         -- CSV record number, from 1
    status            VARCHAR(10) NOT NULL,         -- VALID, ERROR, REMOVED
    ticket_id         TEXT,
    title             TEXT,
    description       TEXT,
    author            TEXT,
    resolved_date     TEXT,
    symptoms          TEXT,
    root_cause        TEXT,
    resolution_steps  TEXT,
    workaround        TEXT,
    scope             TEXT,
    errors            JSONB       NOT NULL DEFAULT '[]',   -- [{code, field, params}]
    PRIMARY KEY (preview_id, row_no)
);
