--liquibase formatted sql

--changeset tracker:019-tracker-suggestion-uuid-keys

-- Team rule (backend/AGENTS.md, Naming): keys and references are UUIDs. suggestion_id becomes a UUID key and
-- project_id references the Project's public_id; job_ref goes, the worker's job carries suggestion_id instead.
-- Existing rows keep their data: a new key, and their Project's public_id.
ALTER TABLE suggestion_result ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE suggestion_result ADD COLUMN new_project_id UUID;
UPDATE suggestion_result s SET new_project_id = p.public_id FROM project p WHERE p.project_id = s.project_id;

DROP INDEX idx_suggestion_project;
ALTER TABLE suggestion_result DROP CONSTRAINT uq_suggestion_job_ref;
ALTER TABLE suggestion_result DROP COLUMN job_ref;
ALTER TABLE suggestion_result DROP COLUMN project_id;
ALTER TABLE suggestion_result DROP COLUMN suggestion_id;

ALTER TABLE suggestion_result RENAME COLUMN new_id TO suggestion_id;
ALTER TABLE suggestion_result RENAME COLUMN new_project_id TO project_id;
ALTER TABLE suggestion_result ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE suggestion_result ADD CONSTRAINT pk_suggestion_result PRIMARY KEY (suggestion_id);
ALTER TABLE suggestion_result
    ADD CONSTRAINT fk_suggestion_project FOREIGN KEY (project_id) REFERENCES project (public_id);
CREATE INDEX idx_suggestion_project ON suggestion_result (project_id, created_at);
