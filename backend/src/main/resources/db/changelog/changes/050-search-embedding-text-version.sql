--liquibase formatted sql

--changeset search:050-search-embedding-text-version

-- search AC-005.4, AC-005.10 (KAN-380): the embedding text now includes the ticket description. Rows written before
-- this change keep version 1 and stay in use until the catch-up job embeds them again as version 2.
ALTER TABLE ticket_embedding ADD COLUMN text_version SMALLINT NOT NULL DEFAULT 1;

--rollback ALTER TABLE ticket_embedding DROP COLUMN text_version;
