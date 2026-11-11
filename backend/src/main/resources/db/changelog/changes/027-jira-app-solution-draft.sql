--liquibase formatted sql

--changeset ticket:027-jira-app-solution-draft

-- jira-app STORY-005 (KAN-126): a Solution Form Draft, one per creator and Jira issue, saved unvalidated (GBR-26).
-- The Solution Form ticket itself is a `ticket` row (source SOLUTION_FORM); `uq_ticket_ticket_id` on
-- source_ticket_id already keeps one ticket per issue (AC-004.3).
CREATE TABLE solution_draft (
    solution_draft_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id        UUID         NOT NULL REFERENCES app_user (id),
    project_id        UUID         NOT NULL REFERENCES project (public_id),
    jira_issue_key    VARCHAR(50)  NOT NULL,
    content           JSONB        NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_solution_draft UNIQUE (creator_id, project_id, jira_issue_key)
);

--rollback DROP TABLE solution_draft;
