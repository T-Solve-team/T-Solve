--liquibase formatted sql
--changeset review:012-review-search-restore-time splitStatements:false
-- Retain the pre-upgrade time rather than a trigger-generated migration timestamp.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
               AND table_name = 'ticket' AND column_name = 'review_search_previous_updated_at') THEN
        ALTER TABLE ticket DISABLE TRIGGER trg_ticket_updated_at;
        UPDATE ticket SET updated_at = review_search_previous_updated_at;
        ALTER TABLE ticket ENABLE TRIGGER trg_ticket_updated_at;
        ALTER TABLE ticket DROP COLUMN review_search_previous_updated_at;
    END IF;
END $$;
