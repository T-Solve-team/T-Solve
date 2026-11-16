--liquibase formatted sql

--changeset workspace:042-workspace-jira-cloud-id
-- KAN-344: the scoped service-account token is sent as Bearer to api.atlassian.com/ex/jira/{cloudId}.
-- Null on rows set up before KAN-344 until the Admin enters a new token (workspace AC-007.11, AC-008.4).
ALTER TABLE workspace ADD COLUMN jira_cloud_id VARCHAR(36);
--rollback ALTER TABLE workspace DROP COLUMN jira_cloud_id;
