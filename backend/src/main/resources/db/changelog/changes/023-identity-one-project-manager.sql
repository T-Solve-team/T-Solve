--liquibase formatted sql

--changeset identity:023-identity-one-project-manager
CREATE UNIQUE INDEX uq_membership_one_manager ON project_membership (project_id)
    WHERE role_in_project = 'PROJECT_MANAGER';

--rollback DROP INDEX uq_membership_one_manager;
