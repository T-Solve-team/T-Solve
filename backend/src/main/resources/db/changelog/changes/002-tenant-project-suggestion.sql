--liquibase formatted sql

--changeset tracker:002-tenant-project-suggestion

-- Temporary: the tenant tables the Jira App events need, copied from 02_tenant.sql (DB design 31/10), until the
-- team's changeset for the whole tenant schema replaces this one. One database per Workspace: no workspace_id.

CREATE TABLE department (
    department_id       BIGSERIAL PRIMARY KEY,
    default_project_id  BIGINT,                      -- FK added after project; NULL = no Chatbot issues
    name                VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_department_name UNIQUE (name)
);

CREATE TABLE project (                               -- never deleted
    project_id        BIGSERIAL PRIMARY KEY,
    department_id     BIGINT       NOT NULL REFERENCES department (department_id),
    jira_project_key  VARCHAR(20)  NOT NULL,
    field_mapping     JSONB,
    done_status       VARCHAR(100),
    review_range      INT,                           -- days allowed for review
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_project_jira_key UNIQUE (jira_project_key)
);
CREATE INDEX idx_project_department ON project (department_id);

ALTER TABLE department
    ADD CONSTRAINT fk_department_default_project
    FOREIGN KEY (default_project_id) REFERENCES project (project_id);

CREATE TABLE suggestion_result (                     -- one result per Jira issue (GBR-36, Forge retries)
    suggestion_id       BIGSERIAL PRIMARY KEY,
    project_id          BIGINT      NOT NULL REFERENCES project (project_id),
    jira_issue_key      VARCHAR(50) NOT NULL,
    sources_used        JSONB       NOT NULL DEFAULT '[]',
    suggested_solution  TEXT,
    reporter_answer     VARCHAR(5),                  -- NULL until reporter answers
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_suggestion_issue UNIQUE (jira_issue_key),
    CONSTRAINT ck_suggestion_answer CHECK (reporter_answer IN ('YES', 'NO'))
);
CREATE INDEX idx_suggestion_project ON suggestion_result (project_id, created_at);
