--liquibase formatted sql

--changeset identity:046-identity-space-manager-many
ALTER TABLE space_manager DROP CONSTRAINT space_manager_pkey;
ALTER TABLE space_manager ADD PRIMARY KEY (space_id, user_id);

-- Rollback only works while no Space has two space_manager rows.
--rollback ALTER TABLE space_manager DROP CONSTRAINT space_manager_pkey;
--rollback ALTER TABLE space_manager ADD PRIMARY KEY (space_id);
