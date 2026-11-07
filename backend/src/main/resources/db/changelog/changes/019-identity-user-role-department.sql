--liquibase formatted sql

--changeset identity:019-identity-user-role-department
-- One database per Workspace: no workspace_id. Identity Task 26b reuses user_role.
CREATE TABLE user_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL CHECK (role IN ('TICKET_RESOLVER', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER', 'ADMIN')),
    assigned_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, role)
);
CREATE INDEX idx_user_role_role ON user_role (role);
ALTER TABLE app_user ADD COLUMN department_id BIGINT NULL REFERENCES department (department_id);
CREATE INDEX idx_app_user_department ON app_user (department_id);

--rollback DROP INDEX idx_app_user_department;
--rollback ALTER TABLE app_user DROP COLUMN department_id;
--rollback DROP TABLE user_role;
