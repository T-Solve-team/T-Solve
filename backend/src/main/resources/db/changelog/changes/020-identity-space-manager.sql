--liquibase formatted sql

--changeset identity:020-identity-space-manager
-- One database per Workspace: no workspace_id. The wiki module reuses these Space tables.
CREATE TABLE space (
    space_id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    department_id BIGINT NOT NULL UNIQUE REFERENCES department (department_id),
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'HIDDEN'))
);
CREATE TABLE space_manager (
    space_id BIGINT PRIMARY KEY REFERENCES space (space_id),
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_space_manager_user ON space_manager (user_id);

--rollback DROP TABLE space_manager;
--rollback DROP TABLE space;
