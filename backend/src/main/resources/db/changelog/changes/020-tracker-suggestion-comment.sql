--liquibase formatted sql

--changeset tracker:020-tracker-suggestion-comment

-- Tracker STORY-012 (KAN-131): the MSG80 comment posted on the issue, so a rerun of the suggestion job never posts it
-- twice. NULL until the comment is posted. Triage's read-only view of this table does not map these columns.
ALTER TABLE suggestion_result ADD COLUMN jira_comment_id VARCHAR(50);
ALTER TABLE suggestion_result ADD COLUMN commented_at TIMESTAMPTZ;
