--liquibase formatted sql

--changeset identity:047-identity-space-manager-many
ALTER TABLE space_manager DROP CONSTRAINT space_manager_pkey;
ALTER TABLE space_manager ADD PRIMARY KEY (space_id, user_id);

-- Note: the rollback statements below need every Space to have at most one manager row.
--rollback ALTER TABLE space_manager DROP CONSTRAINT space_manager_pkey;
--rollback ALTER TABLE space_manager ADD PRIMARY KEY (space_id);
