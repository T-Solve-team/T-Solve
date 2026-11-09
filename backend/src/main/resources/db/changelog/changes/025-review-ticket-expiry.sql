--liquibase formatted sql

--changeset review:025-review-ticket-expiry
ALTER TABLE ticket
    ADD COLUMN expired_at TIMESTAMPTZ,
    ADD COLUMN status_before_expiry VARCHAR(20),
    ADD CONSTRAINT ck_ticket_status_before_expiry
        CHECK (status_before_expiry IS NULL OR status_before_expiry IN ('PENDING_REVIEW', 'CHANGES_REQUESTED'));

CREATE INDEX idx_ticket_expirable_due ON ticket (review_due_date)
    WHERE status IN ('PENDING_REVIEW', 'CHANGES_REQUESTED');

--rollback DROP INDEX idx_ticket_expirable_due;
--rollback ALTER TABLE ticket DROP CONSTRAINT ck_ticket_status_before_expiry, DROP COLUMN status_before_expiry, DROP COLUMN expired_at;
