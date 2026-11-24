--liquibase formatted sql

--changeset platform:051-platform-tenant-identity

-- platform AC-001.28 (KAN-253): which Workspace this tenant database belongs to. At most one row; written once at
-- provisioning by the owner, never by the runtime user (the grants are the provisioning script's, not a changeset's).
CREATE TABLE tenant_identity (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    tenant_id UUID NOT NULL
);

--rollback DROP TABLE tenant_identity;
