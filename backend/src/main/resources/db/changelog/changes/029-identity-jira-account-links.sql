--liquibase formatted sql

--changeset identity:029-identity-jira-account-links
CREATE TABLE jira_account_links (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tracker_account_id VARCHAR(128) NOT NULL,
    account_id         UUID         NOT NULL REFERENCES app_user (id),
    linked_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_jira_account_links_tracker_account_id UNIQUE (tracker_account_id)
);

CREATE INDEX idx_jira_account_links_account_id ON jira_account_links (account_id);

--rollback DROP TABLE jira_account_links;
