--liquibase formatted sql

--changeset ticket:011-ticket-import-type

-- The file's import type (import-ticket-list D15); previews that exist today are files with Solutions.
ALTER TABLE import_preview ADD COLUMN import_type VARCHAR(20) NOT NULL DEFAULT 'WITH_SOLUTION';
-- NEEDS_SOLUTION has 14 characters.
ALTER TABLE import_preview_row ALTER COLUMN status TYPE VARCHAR(20);
