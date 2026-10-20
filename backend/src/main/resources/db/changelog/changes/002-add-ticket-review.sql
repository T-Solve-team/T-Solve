--liquibase formatted sql

--changeset ticket-review:002-add-ticket-review
CREATE TABLE ticket_review (
    id BIGSERIAL PRIMARY KEY,
    source_ticket_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    project_id BIGINT NOT NULL CHECK (project_id > 0),
    project_name VARCHAR(255) NOT NULL,
    source VARCHAR(30) NOT NULL CHECK (source IN ('SOLUTION_FORM', 'JIRA_IMPORT', 'CSV', 'MANUAL')),
    writer_id VARCHAR(150) NOT NULL,
    writer_name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_REVIEW', 'CHANGES_REQUESTED', 'PUBLISHED', 'REJECTED', 'UNPUBLISHED', 'EXPIRED')),
    entered_queue_at TIMESTAMPTZ NOT NULL,
    review_due_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    review_comment TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_review_source_ticket UNIQUE (source, source_ticket_id),
    CONSTRAINT ck_review_dates CHECK (review_due_at >= entered_queue_at)
);
CREATE INDEX idx_review_queue ON ticket_review (project_id, status, source, review_due_at, id);

CREATE TABLE ticket_review_solution (
    ticket_id BIGINT PRIMARY KEY REFERENCES ticket_review(id),
    symptoms TEXT,
    root_cause TEXT,
    resolution_steps TEXT,
    workaround TEXT,
    scope_of_applicability TEXT
);

CREATE TABLE ticket_review_evidence (
    ticket_id BIGINT NOT NULL REFERENCES ticket_review(id),
    evidence_id VARCHAR(255) NOT NULL,
    name VARCHAR(500) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 0 AND 10485760),
    PRIMARY KEY (ticket_id, evidence_id)
);

CREATE TABLE ticket_review_decision (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES ticket_review(id),
    actor_id VARCHAR(150) NOT NULL,
    actor_name VARCHAR(255) NOT NULL,
    writer_id VARCHAR(150) NOT NULL,
    writer_name VARCHAR(255) NOT NULL,
    action VARCHAR(30) NOT NULL CHECK (action IN ('APPROVE', 'REJECT', 'REQUEST_CHANGES')),
    comment TEXT,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_decision_comment CHECK (
        action = 'APPROVE' OR (comment IS NOT NULL AND length(btrim(comment)) BETWEEN 1 AND 1000)
    )
);
CREATE INDEX idx_review_history ON ticket_review_decision (ticket_id, decided_at DESC, id DESC);

CREATE TABLE ticket_review_notification (
    id BIGSERIAL PRIMARY KEY,
    decision_id BIGINT NOT NULL UNIQUE REFERENCES ticket_review_decision(id),
    recipient_id VARCHAR(150) NOT NULL,
    action VARCHAR(30) NOT NULL CHECK (action IN ('REJECT', 'REQUEST_CHANGES')),
    comment TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);
CREATE INDEX idx_review_pending_notifications ON ticket_review_notification (id) WHERE delivered_at IS NULL;
