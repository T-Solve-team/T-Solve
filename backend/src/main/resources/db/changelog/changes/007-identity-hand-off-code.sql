--liquibase formatted sql

--changeset identity:007-identity-hand-off-code

-- A single-use code that hands a signed-in Account from login. to the target site (identity AC-001.16). Interim: it
-- lives in the one changelog until Task 14 moves it into the Registry database. The browser gets "<id>.<HMAC>".
-- account_id has no foreign key: app_user is in another database.

CREATE TABLE hand_off_code (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target           VARCHAR(20)   NOT NULL,          -- WORKSPACE, PLATFORM_CONSOLE
    workspace_id     UUID,                            -- null for the Platform Console
    account_id       UUID,                            -- null for the Platform Console
    operator_email   VARCHAR(254),                    -- Platform Console only
    site_host        VARCHAR(255)  NOT NULL,
    return_to        VARCHAR(2048) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    expires_at       TIMESTAMPTZ   NOT NULL,          -- created_at + 60 seconds
    used_at          TIMESTAMPTZ,                     -- set once, by one conditional UPDATE
    CONSTRAINT ck_hand_off_code_one_target CHECK (
        (target = 'WORKSPACE' AND workspace_id IS NOT NULL AND account_id IS NOT NULL AND operator_email IS NULL)
        OR (target = 'PLATFORM_CONSOLE' AND workspace_id IS NULL AND account_id IS NULL AND operator_email IS NOT NULL)
    )
);

CREATE INDEX idx_hand_off_code_expires_at ON hand_off_code (expires_at);
