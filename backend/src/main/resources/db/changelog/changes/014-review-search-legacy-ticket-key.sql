--liquibase formatted sql

--changeset review:014-review-search-legacy-ticket-key splitStatements:false
-- Search's 010 and 012 changesets reference ticket(id). A database already running Review has replaced that key
-- with the UUID ticket_id, so give it a temporary unique id for them to reference; 016 drops it again.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
                   AND table_name = 'ticket' AND column_name = 'id') THEN
        ALTER TABLE ticket ADD COLUMN id BIGSERIAL;
        ALTER TABLE ticket ADD CONSTRAINT uq_ticket_review_legacy_id UNIQUE (id);
    END IF;
END $$;
