--liquibase formatted sql

--changeset ticket:048-jira-app-issue-done-state

-- jira-app AC-005.9 (KAN-371): whether a Project's Jira issue is in the Done status category, as last seen by the
-- avi:jira:updated:issue trigger. The Draft reminder lists a Draft only while its issue is recorded Done (AC-011.2);
-- an issue the trigger has never seen has no row and is not reminded.
CREATE TABLE jira_issue_done_state (
    jira_issue_done_state_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id               UUID         NOT NULL REFERENCES project (public_id),
    jira_issue_key           VARCHAR(50)  NOT NULL,
    done                     BOOLEAN      NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_jira_issue_done_state UNIQUE (project_id, jira_issue_key)
);

--rollback DROP TABLE jira_issue_done_state;
