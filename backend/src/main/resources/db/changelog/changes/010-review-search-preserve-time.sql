--liquibase formatted sql
--changeset review:010-review-search-preserve-time splitStatements:false
-- Search and Review independently introduced updated_at. Preserve an already-applied Review value
-- before the unchanged Search changeset adds that column. Never rewrite applied SQL/checksums.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM databasechangelog WHERE id = '009-search-ticket-updated-at' AND author = 'search')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
                   AND table_name = 'ticket' AND column_name = 'updated_at') THEN
        ALTER TABLE ticket RENAME COLUMN updated_at TO review_search_previous_updated_at;
    END IF;
END $$;
