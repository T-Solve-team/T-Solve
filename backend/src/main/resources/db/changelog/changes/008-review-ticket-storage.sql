--liquibase formatted sql

--changeset review:008-review-ticket-storage

-- Remap existing import decisions before replacing the ticket row key.
ALTER TABLE ticket RENAME COLUMN ticket_id TO source_ticket_id;
ALTER TABLE ticket ADD COLUMN ticket_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE ticket_import_decision ADD COLUMN new_ticket_id UUID;

UPDATE ticket_import_decision AS decision
SET new_ticket_id = ticket.ticket_id
FROM ticket
WHERE ticket.id = decision.ticket_id;

ALTER TABLE ticket_import_decision ALTER COLUMN new_ticket_id SET NOT NULL;
ALTER TABLE ticket_import_decision DROP COLUMN ticket_id;
ALTER TABLE ticket DROP COLUMN id;
ALTER TABLE ticket ADD PRIMARY KEY (ticket_id);
ALTER TABLE ticket_import_decision RENAME COLUMN new_ticket_id TO ticket_id;
ALTER TABLE ticket_import_decision ADD FOREIGN KEY (ticket_id) REFERENCES ticket (ticket_id);
CREATE INDEX idx_ticket_import_decision_ticket ON ticket_import_decision (ticket_id);

ALTER TABLE ticket
    ADD COLUMN writer_id UUID REFERENCES app_user (id),
    ADD COLUMN review_due_date DATE,
    ADD COLUMN rejection_reason TEXT,
    ADD COLUMN change_request_comment TEXT,
    ADD COLUMN source_link VARCHAR(500),
    ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE ticket SET updated_at = created_at;
ALTER TABLE ticket ALTER COLUMN updated_at SET DEFAULT now(), ALTER COLUMN updated_at SET NOT NULL;
CREATE INDEX idx_ticket_writer ON ticket (writer_id);

CREATE TABLE evidence (
    evidence_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID         NOT NULL REFERENCES ticket (ticket_id) ON DELETE CASCADE,
    file_name   VARCHAR(255) NOT NULL,
    file_type   VARCHAR(100) NOT NULL,
    file_size   BIGINT       NOT NULL,
    upload_date TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_evidence_size CHECK (file_size >= 0)
);
CREATE INDEX idx_evidence_ticket ON evidence (ticket_id);

CREATE TABLE review_decision (
    review_decision_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id          UUID        NOT NULL REFERENCES ticket (ticket_id),
    project_manager_id UUID        NOT NULL REFERENCES app_user (id),
    decision           VARCHAR(20) NOT NULL,
    reason_or_comment  TEXT,
    decided_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_review_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED'))
);
CREATE INDEX idx_review_ticket ON review_decision (ticket_id);
