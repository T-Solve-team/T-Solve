--liquibase formatted sql

--changeset ticket:010-ticket-jira-import

-- Jira import (KAN-45, UC-20) and import Drafts (GBR-26). A pulled Jira issue is a row of the Import Preview.

-- A Jira preview has no file; a Draft (draft_saved_at set) survives the daily cleanup and a new pull or upload.
ALTER TABLE import_preview ALTER COLUMN file_name DROP NOT NULL;
ALTER TABLE import_preview ADD COLUMN resolved_from  DATE;                          -- Jira only
ALTER TABLE import_preview ADD COLUMN resolved_to    DATE;                          -- Jira only
ALTER TABLE import_preview ADD COLUMN more_available BOOLEAN NOT NULL DEFAULT false; -- MSG35
ALTER TABLE import_preview ADD COLUMN draft_saved_at TIMESTAMPTZ;                   -- NULL = not a Draft
DROP INDEX uq_import_preview_owner;
CREATE UNIQUE INDEX uq_import_preview_owner_open ON import_preview (owner) WHERE draft_saved_at IS NULL;
CREATE INDEX idx_import_preview_owner_draft ON import_preview (owner, draft_saved_at DESC)
    WHERE draft_saved_at IS NOT NULL;

-- NEEDS_SOLUTION has 14 characters (also widened by import-ticket-list's import-type changeset; same type).
ALTER TABLE import_preview_row ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE import_preview_row ADD COLUMN source_url TEXT;                          -- Jira only
ALTER TABLE import_preview_row ADD COLUMN comments   JSONB NOT NULL DEFAULT '[]';   -- Jira only: [{author, created, body}]
