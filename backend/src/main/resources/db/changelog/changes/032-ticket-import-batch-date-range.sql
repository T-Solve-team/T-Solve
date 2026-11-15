--liquibase formatted sql

--changeset ticket:032-ticket-import-batch-date-range
ALTER TABLE import_batch ADD COLUMN date_from DATE, ADD COLUMN date_to DATE;

--rollback ALTER TABLE import_batch DROP COLUMN date_from, DROP COLUMN date_to;
