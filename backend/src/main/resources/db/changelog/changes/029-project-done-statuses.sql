--liquibase formatted sql

--changeset project:029-project-done-statuses
ALTER TABLE project ALTER COLUMN done_status TYPE JSONB USING CASE WHEN
  done_status IS NULL THEN NULL ELSE jsonb_build_array(done_status) END;

--rollback ALTER TABLE project ALTER COLUMN done_status TYPE VARCHAR(100) USING done_status ->> 0;
