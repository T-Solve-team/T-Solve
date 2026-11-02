-- T-Solve catalog database (one per deployment). PostgreSQL.
-- Source: Report/Sua_31-10/ERD_T-Solve_31-10.drawio. Only holds what is needed to find a company's tenant database.
-- Create the database first, e.g.: CREATE DATABASE tsolve_catalog;

CREATE TABLE tenant_workspace (
    workspace_id     BIGSERIAL PRIMARY KEY,
    site_name        VARCHAR(63)  NOT NULL,          -- subdomain: fpt.tsolve.io.vn -> fpt
    jira_cloud_id    VARCHAR(64),                    -- set when Admin enters the Jira site (UC-08)
    db_host          VARCHAR(255) NOT NULL,
    db_port          INT          NOT NULL DEFAULT 5432,
    db_name          VARCHAR(63)  NOT NULL,
    db_username      VARCHAR(63)  NOT NULL,
    db_password_ref  VARCHAR(100) NOT NULL,          -- name of the secret / env var, never the password
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_site_name   UNIQUE (site_name),
    CONSTRAINT uq_tenant_jira_cloud  UNIQUE (jira_cloud_id),
    CONSTRAINT uq_tenant_db_name     UNIQUE (db_name),
    CONSTRAINT ck_tenant_status      CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE workspace_email (                       -- one email belongs to one Workspace only (MSG155)
    email         VARCHAR(254) PRIMARY KEY,          -- stored lower-case
    workspace_id  BIGINT       NOT NULL REFERENCES tenant_workspace (workspace_id),
    CONSTRAINT ck_workspace_email_lower CHECK (email = lower(email))
);
CREATE INDEX idx_workspace_email_workspace ON workspace_email (workspace_id);
