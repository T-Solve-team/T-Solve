--liquibase formatted sql

--changeset ticket:057-ticket-optional-sections
--comment: KAN-474: only Root cause and Resolution steps are required; an empty Symptoms or Scope is stored as null
ALTER TABLE ticket ALTER COLUMN symptoms DROP NOT NULL;
ALTER TABLE ticket ALTER COLUMN scope DROP NOT NULL;
--rollback UPDATE ticket SET symptoms = '' WHERE symptoms IS NULL;
--rollback UPDATE ticket SET scope = '' WHERE scope IS NULL;
--rollback ALTER TABLE ticket ALTER COLUMN symptoms SET NOT NULL;
--rollback ALTER TABLE ticket ALTER COLUMN scope SET NOT NULL;
