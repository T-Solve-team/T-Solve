--liquibase formatted sql
--changeset review:011-review-search-reserve-time splitStatements:false
-- Preserve Search's column before the unchanged Review storage changeset runs.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id = '008-review-ticket-storage' AND author = 'review')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
                   AND table_name = 'ticket' AND column_name = 'updated_at') THEN
        ALTER TABLE ticket RENAME COLUMN updated_at TO review_search_previous_updated_at;
    END IF;
END $$;
