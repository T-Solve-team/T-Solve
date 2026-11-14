--liquibase formatted sql

--changeset ticket:031-ticket-manual-entry

-- Manual entry (KAN-133, UC-22): Evidence files live in the Workspace file storage; the database keeps their key.
ALTER TABLE evidence ADD COLUMN storage_key VARCHAR(200);            -- FileStorage key; NULL only on rows from before KAN-133
-- Manual Draft only: [{id, storageKey, fileName, fileType, fileSize}]
ALTER TABLE import_preview ADD COLUMN evidence JSONB NOT NULL DEFAULT '[]';

--rollback ALTER TABLE import_preview DROP COLUMN evidence;
--rollback ALTER TABLE evidence DROP COLUMN storage_key;
