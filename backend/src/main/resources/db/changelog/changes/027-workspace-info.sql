--liquibase formatted sql

--changeset workspace:027-workspace-info
-- PostgreSQL preserves the legacy rows and foreign-key targets when their table is renamed.
ALTER TABLE workspace RENAME TO legacy_workspace;

CREATE TABLE workspace (
    workspace_id              UUID         CONSTRAINT pk_workspace_info PRIMARY KEY,
    company_name              VARCHAR(150) NOT NULL,
    sign_in_provider          VARCHAR(30)  NOT NULL,
    jira_site_address         VARCHAR(255),
    service_account_email     VARCHAR(254),
    service_account_api_token TEXT,
    token_expiry_date         DATE,
    singleton                 BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_workspace_singleton UNIQUE (singleton),
    CONSTRAINT ck_workspace_singleton CHECK (singleton)
);

--rollback DROP TABLE workspace;
--rollback ALTER TABLE legacy_workspace RENAME TO workspace;
