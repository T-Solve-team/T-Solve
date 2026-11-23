--liquibase formatted sql

--changeset platform:C001-registry-tenant-workspace

CREATE TABLE tenant_workspace (
    workspace_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_name                VARCHAR(63)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    db_name                  VARCHAR(255) NOT NULL,
    db_user                  VARCHAR(255) NOT NULL,
    db_password              TEXT         NOT NULL,
    schema_version           VARCHAR(255) NOT NULL,
    jira_site_url            VARCHAR(500),
    jira_cloud_id            VARCHAR(255),
    google_domain            VARCHAR(255) NOT NULL,
    keycloak_organization_id VARCHAR(255),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_workspace_site_name UNIQUE (site_name),
    CONSTRAINT uq_tenant_workspace_db_name UNIQUE (db_name),
    CONSTRAINT uq_tenant_workspace_jira_cloud_id UNIQUE (jira_cloud_id),
    CONSTRAINT uq_tenant_workspace_keycloak_org UNIQUE (keycloak_organization_id),
    CONSTRAINT ck_tenant_workspace_site_name CHECK (site_name ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$')
);
