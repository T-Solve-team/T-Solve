--liquibase formatted sql

--changeset identity:004-identity-sign-in-states

-- One row per sign-in attempt (identity STORY-001). Shared (non-tenant) table: it is read before any Workspace is
-- known. workspace_id has no FK yet: the workspaces table belongs to the platform module.
-- The browser only gets "<id>.<HMAC>"; nonce and pkce_verifier never leave the server.

CREATE TABLE sign_in_states (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target             VARCHAR(20)   NOT NULL,        -- WORKSPACE, PLATFORM_CONSOLE, JIRA_LINK
    workspace_id       UUID,                          -- null for the Platform Console
    tracker_account_id VARCHAR(255),                  -- Jira link only
    return_to          VARCHAR(2048) NOT NULL,
    nonce              VARCHAR(128)  NOT NULL,
    pkce_verifier      VARCHAR(128)  NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    expires_at         TIMESTAMPTZ   NOT NULL,        -- created_at + 10 minutes
    used_at            TIMESTAMPTZ,                   -- set once, by one conditional UPDATE
    CONSTRAINT ck_sign_in_states_one_target CHECK (
        (target = 'WORKSPACE'        AND workspace_id IS NOT NULL AND tracker_account_id IS NULL)
        OR (target = 'PLATFORM_CONSOLE' AND workspace_id IS NULL     AND tracker_account_id IS NULL)
        OR (target = 'JIRA_LINK'        AND workspace_id IS NOT NULL AND tracker_account_id IS NOT NULL)
    )
);

-- the scheduled cleanup deletes by expires_at
CREATE INDEX idx_sign_in_states_expires_at ON sign_in_states (expires_at);
