--liquibase formatted sql

--changeset ticket:008-ticket-import-project-uuid

-- Project ids are UUIDs (import-ticket-list D10): the web app takes the Project from the session.
-- Earlier stub ids keep a stable value, written in hex: 1 -> 00000000-0000-0000-0000-000000000001, 10 -> ...00000000000a.
ALTER TABLE import_batch   ALTER COLUMN project_id TYPE UUID USING lpad(to_hex(project_id), 32, '0')::uuid;
ALTER TABLE ticket         ALTER COLUMN project_id TYPE UUID USING lpad(to_hex(project_id), 32, '0')::uuid;
ALTER TABLE import_preview ALTER COLUMN project_id TYPE UUID USING lpad(to_hex(project_id), 32, '0')::uuid;
