--liquibase formatted sql

--changeset platform:C002-registry-workspace-email

CREATE TABLE workspace_email (
    email        VARCHAR(320) PRIMARY KEY CHECK (email = lower(email)),
    workspace_id UUID NOT NULL REFERENCES tenant_workspace (workspace_id)
);

CREATE INDEX idx_workspace_email_workspace_id ON workspace_email (workspace_id);
