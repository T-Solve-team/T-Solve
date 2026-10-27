--liquibase formatted sql

--changeset tracker:002-jira-issue-events

-- Jira integration: the Jira site registered for each Workspace, and the issue events Jira sends us.
-- Events are stored first and processed later by a background worker.

CREATE TABLE jira_site (
    id            BIGSERIAL PRIMARY KEY,
    workspace_id  BIGINT       NOT NULL UNIQUE REFERENCES workspace (id),   -- one site per Workspace
    site_url      VARCHAR(255) NOT NULL,
    cloud_id      VARCHAR(64)  NOT NULL UNIQUE,                             -- AC-002.5
    registered_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE issue_event (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT       NOT NULL REFERENCES workspace (id),   -- tenant (AC-003.7)
    cloud_id         VARCHAR(64)  NOT NULL,
    issue_id         VARCHAR(32)  NOT NULL,
    issue_key        VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(20)  NOT NULL,          -- ISSUE_CREATED, ISSUE_UPDATED
    event_timestamp  BIGINT       NOT NULL,          -- Forge payload `timestamp` (epoch ms)
    status           VARCHAR(20)  NOT NULL,          -- RECEIVED, PROCESSING, PROCESSED, IGNORED, FAILED
    attempts         SMALLINT     NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL,
    claimed_at       TIMESTAMPTZ,
    token_ciphertext BYTEA,                          -- AES-GCM(iv || ciphertext || tag); NULL once finished
    comment_posted   BOOLEAN      NOT NULL DEFAULT FALSE,   -- AC-005.4
    error_type       VARCHAR(50),
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    CONSTRAINT uq_issue_event UNIQUE (cloud_id, issue_id, event_type, event_timestamp)   -- AC-003.4
);
CREATE INDEX idx_issue_event_due ON issue_event (status, next_attempt_at);
CREATE INDEX idx_issue_event_received ON issue_event (received_at);
CREATE INDEX idx_issue_event_workspace ON issue_event (workspace_id);
