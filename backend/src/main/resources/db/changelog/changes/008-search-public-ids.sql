--liquibase formatted sql

--changeset search:008-search-public-ids

ALTER TABLE ticket
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD CONSTRAINT uq_ticket_public_id UNIQUE (public_id);

ALTER TABLE project
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD CONSTRAINT uq_project_public_id UNIQUE (public_id);

ALTER TABLE department
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD CONSTRAINT uq_department_public_id UNIQUE (public_id);

-- Development databases only: rollback discards the public ids already exposed by the API.
--rollback ALTER TABLE department DROP COLUMN public_id;
--rollback ALTER TABLE project DROP COLUMN public_id;
--rollback ALTER TABLE ticket DROP COLUMN public_id;
