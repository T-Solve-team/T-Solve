--liquibase formatted sql

-- KAN-137: one table for every decision on a Ticket. review_decision is renamed so its rows, ids and foreign keys
-- stay; the import decisions are copied in and their table goes. The rollback exists for the workspace migration test
-- (BUG-243), which rolls back every changeset after 035; a deployed database is fixed forward (backend/AGENTS.md §7).
--changeset ticket:038-ticket-decision
ALTER TABLE review_decision RENAME TO ticket_decision;
ALTER TABLE ticket_decision RENAME COLUMN review_decision_id TO ticket_decision_id;
ALTER TABLE ticket_decision RENAME COLUMN project_manager_id TO reviewer_id;
ALTER TABLE ticket_decision RENAME COLUMN reason_or_comment TO feedback;
ALTER TABLE ticket_decision RENAME CONSTRAINT review_decision_pkey TO ticket_decision_pkey;
ALTER TABLE ticket_decision RENAME CONSTRAINT ck_review_decision TO ck_ticket_decision;
ALTER INDEX idx_review_ticket RENAME TO idx_ticket_decision_ticket;
ALTER INDEX idx_review_decision_decided_at RENAME TO idx_ticket_decision_decided_at;
ALTER TABLE ticket_decision ALTER COLUMN reviewer_id DROP NOT NULL;
-- Sign-in name of the Project Manager who published an import or a manual entry.
ALTER TABLE ticket_decision ADD COLUMN reviewer VARCHAR(150);
ALTER TABLE ticket_decision ADD CONSTRAINT ck_ticket_decision_reviewer
    CHECK (reviewer_id IS NOT NULL OR reviewer IS NOT NULL);

-- A value the CHECK does not allow fails the changeset instead of being relabelled.
INSERT INTO ticket_decision (ticket_id, reviewer, decision, feedback, decided_at)
SELECT ticket_id, reviewer, CASE decision WHEN 'APPROVE' THEN 'APPROVED' ELSE decision END, reason, decided_at
FROM ticket_import_decision;
DROP TABLE ticket_import_decision;
--rollback CREATE TABLE ticket_import_decision (id BIGSERIAL PRIMARY KEY, ticket_id UUID NOT NULL REFERENCES ticket (ticket_id), reviewer VARCHAR(150) NOT NULL, decision VARCHAR(30) NOT NULL, reason TEXT, decided_at TIMESTAMPTZ NOT NULL DEFAULT now());
--rollback CREATE INDEX idx_ticket_import_decision_ticket ON ticket_import_decision (ticket_id);
--rollback INSERT INTO ticket_import_decision (ticket_id, reviewer, decision, reason, decided_at) SELECT ticket_id, reviewer, 'APPROVE', feedback, decided_at FROM ticket_decision WHERE reviewer_id IS NULL;
--rollback DELETE FROM ticket_decision WHERE reviewer_id IS NULL;
--rollback ALTER TABLE ticket_decision DROP CONSTRAINT ck_ticket_decision_reviewer;
--rollback ALTER TABLE ticket_decision DROP COLUMN reviewer;
--rollback ALTER TABLE ticket_decision ALTER COLUMN reviewer_id SET NOT NULL;
--rollback ALTER INDEX idx_ticket_decision_decided_at RENAME TO idx_review_decision_decided_at;
--rollback ALTER INDEX idx_ticket_decision_ticket RENAME TO idx_review_ticket;
--rollback ALTER TABLE ticket_decision RENAME CONSTRAINT ck_ticket_decision TO ck_review_decision;
--rollback ALTER TABLE ticket_decision RENAME CONSTRAINT ticket_decision_pkey TO review_decision_pkey;
--rollback ALTER TABLE ticket_decision RENAME COLUMN feedback TO reason_or_comment;
--rollback ALTER TABLE ticket_decision RENAME COLUMN reviewer_id TO project_manager_id;
--rollback ALTER TABLE ticket_decision RENAME COLUMN ticket_decision_id TO review_decision_id;
--rollback ALTER TABLE ticket_decision RENAME TO review_decision;
