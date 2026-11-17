--liquibase formatted sql

--changeset chatbot:043-chatbot-answer-jira-issue
ALTER TABLE chatbot_answer ADD COLUMN jira_issue_key VARCHAR(255) NULL;
--rollback ALTER TABLE chatbot_answer DROP COLUMN jira_issue_key;
