--liquibase formatted sql

--changeset review:016-review-search-ticket-refs-attach
-- Point Search's references at the UUID ticket key created by 008-review-ticket-storage, keeping every
-- embedding and feedback row, then drop the old keys kept by 014 and 015.
ALTER TABLE ticket_embedding ADD COLUMN ticket_id UUID;
UPDATE ticket_embedding AS e SET ticket_id = t.ticket_id FROM ticket AS t WHERE t.review_legacy_id = e.legacy_ticket_id;
ALTER TABLE ticket_embedding DROP COLUMN legacy_ticket_id;
ALTER TABLE ticket_embedding ALTER COLUMN ticket_id SET NOT NULL;
ALTER TABLE ticket_embedding ADD CONSTRAINT ticket_embedding_ticket_id_fkey
    FOREIGN KEY (ticket_id) REFERENCES ticket (ticket_id) ON DELETE CASCADE;
ALTER TABLE ticket_embedding ADD CONSTRAINT uq_ticket_embedding_ticket UNIQUE (ticket_id);

ALTER TABLE ticket_feedback ADD COLUMN ticket_id UUID;
UPDATE ticket_feedback AS f SET ticket_id = t.ticket_id FROM ticket AS t WHERE t.review_legacy_id = f.legacy_ticket_id;
ALTER TABLE ticket_feedback DROP COLUMN legacy_ticket_id;
ALTER TABLE ticket_feedback ALTER COLUMN ticket_id SET NOT NULL;
ALTER TABLE ticket_feedback ADD CONSTRAINT ticket_feedback_ticket_id_fkey
    FOREIGN KEY (ticket_id) REFERENCES ticket (ticket_id);
ALTER TABLE ticket_feedback ADD CONSTRAINT uq_ticket_feedback_ticket_user UNIQUE (ticket_id, user_id);

ALTER TABLE ticket DROP COLUMN review_legacy_id;
ALTER TABLE ticket DROP COLUMN IF EXISTS id;
