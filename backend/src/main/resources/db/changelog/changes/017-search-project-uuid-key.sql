--liquibase formatted sql

--changeset search:017-search-project-uuid-key

-- 010 encoded the old numeric key as a UUID; keep the UUID already exposed by the API instead.
ALTER TABLE ticket DISABLE TRIGGER trg_ticket_updated_at;
UPDATE ticket t SET project_id = p.public_id
FROM project p WHERE t.project_id = lpad(to_hex(p.project_id), 32, '0')::uuid;
ALTER TABLE ticket ENABLE TRIGGER trg_ticket_updated_at;
UPDATE import_batch b SET project_id = p.public_id
FROM project p WHERE b.project_id = lpad(to_hex(p.project_id), 32, '0')::uuid;
UPDATE import_preview i SET project_id = p.public_id
FROM project p WHERE i.project_id = lpad(to_hex(p.project_id), 32, '0')::uuid;

-- Dropping the BIGINT columns below also drops their foreign keys and index.
ALTER TABLE suggestion_result ADD COLUMN project_uuid UUID;
UPDATE suggestion_result s SET project_uuid = p.public_id FROM project p WHERE s.project_id = p.project_id;
ALTER TABLE suggestion_result DROP COLUMN project_id;
ALTER TABLE suggestion_result RENAME COLUMN project_uuid TO project_id;
ALTER TABLE suggestion_result ALTER COLUMN project_id SET NOT NULL;
CREATE INDEX idx_suggestion_project ON suggestion_result (project_id, created_at);
ALTER TABLE department ADD COLUMN default_project_uuid UUID;
UPDATE department d SET default_project_uuid = p.public_id FROM project p WHERE d.default_project_id = p.project_id;
ALTER TABLE department DROP COLUMN default_project_id;
ALTER TABLE department RENAME COLUMN default_project_uuid TO default_project_id;

-- Dropping the BIGSERIAL column also drops its owned project_project_id_seq sequence and the old primary key.
ALTER TABLE project DROP COLUMN project_id;
ALTER TABLE project DROP CONSTRAINT uq_project_public_id;
ALTER TABLE project RENAME COLUMN public_id TO project_id;
ALTER TABLE project ADD CONSTRAINT project_pkey PRIMARY KEY (project_id);
ALTER TABLE suggestion_result ADD CONSTRAINT suggestion_result_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES project (project_id);
ALTER TABLE department ADD CONSTRAINT fk_department_default_project
    FOREIGN KEY (default_project_id) REFERENCES project (project_id);

-- Development databases only: rollback assigns new BIGINT keys, retaining the UUIDs as public_id.
-- Ticket/import references stay UUIDs, as they were after 010; they retain the public UUID form.
--rollback ALTER TABLE suggestion_result DROP CONSTRAINT suggestion_result_project_id_fkey;
--rollback ALTER TABLE department DROP CONSTRAINT fk_department_default_project;
--rollback ALTER TABLE project DROP CONSTRAINT project_pkey;
--rollback ALTER TABLE project RENAME COLUMN project_id TO public_id;
--rollback ALTER TABLE project ADD CONSTRAINT uq_project_public_id UNIQUE (public_id);
--rollback ALTER TABLE project ADD COLUMN project_id BIGSERIAL PRIMARY KEY;
--rollback ALTER TABLE suggestion_result ADD COLUMN project_bigint BIGINT;
--rollback UPDATE suggestion_result s SET project_bigint = p.project_id FROM project p WHERE s.project_id = p.public_id;
--rollback ALTER TABLE suggestion_result DROP COLUMN project_id;
--rollback ALTER TABLE suggestion_result RENAME COLUMN project_bigint TO project_id;
--rollback ALTER TABLE suggestion_result ALTER COLUMN project_id SET NOT NULL;
--rollback CREATE INDEX idx_suggestion_project ON suggestion_result (project_id, created_at);
--rollback ALTER TABLE department ADD COLUMN default_project_bigint BIGINT;
--rollback UPDATE department d SET default_project_bigint = p.project_id FROM project p WHERE d.default_project_id = p.public_id;
--rollback ALTER TABLE department DROP COLUMN default_project_id;
--rollback ALTER TABLE department RENAME COLUMN default_project_bigint TO default_project_id;
--rollback ALTER TABLE suggestion_result ADD CONSTRAINT suggestion_result_project_id_fkey FOREIGN KEY (project_id) REFERENCES project (project_id);
--rollback ALTER TABLE department ADD CONSTRAINT fk_department_default_project FOREIGN KEY (default_project_id) REFERENCES project (project_id);
