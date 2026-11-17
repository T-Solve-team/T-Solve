--liquibase formatted sql

--changeset workspace:043-workspace-clear-service-account
-- KAN-349: T-Solve calls Jira as its Jira App; no customer Jira credential stays stored (workspace AC-011.11).
UPDATE workspace SET service_account_email = NULL, service_account_api_token = NULL;
--rollback SELECT 1;
