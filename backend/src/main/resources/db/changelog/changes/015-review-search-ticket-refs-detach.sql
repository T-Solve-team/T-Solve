--liquibase formatted sql

--changeset review:015-review-search-ticket-refs-detach
-- Search's ticket_embedding and ticket_feedback reference ticket(id) BIGINT, which 008-review-ticket-storage
-- replaces with the UUID key ticket_id. Keep the old key and detach those references first, so 008 can run
-- unchanged; 016-review-search-ticket-refs-attach points them at ticket_id afterwards.
ALTER TABLE ticket ADD COLUMN review_legacy_id BIGINT;
-- Copying the key is not an edit: keep updated_at and the Review queue time as they are.
ALTER TABLE ticket DISABLE TRIGGER USER;
UPDATE ticket SET review_legacy_id = id;
ALTER TABLE ticket ENABLE TRIGGER USER;

ALTER TABLE ticket_embedding DROP CONSTRAINT uq_ticket_embedding_ticket;
ALTER TABLE ticket_embedding DROP CONSTRAINT ticket_embedding_ticket_id_fkey;
ALTER TABLE ticket_embedding RENAME COLUMN ticket_id TO legacy_ticket_id;

ALTER TABLE ticket_feedback DROP CONSTRAINT uq_ticket_feedback_ticket_user;
ALTER TABLE ticket_feedback DROP CONSTRAINT ticket_feedback_ticket_id_fkey;
ALTER TABLE ticket_feedback RENAME COLUMN ticket_id TO legacy_ticket_id;
