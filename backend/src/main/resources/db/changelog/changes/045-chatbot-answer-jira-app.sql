--liquibase formatted sql

--changeset chatbot:045-chatbot-answer-jira-app
-- chatbot AC-012.5 (KAN-294): a question asked on the Jira App's Ask T-Solve page is stored without a user, with its
-- Project; a web question keeps its user and has no Project.
ALTER TABLE chatbot_answer ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE chatbot_answer ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'WEB';
ALTER TABLE chatbot_answer ADD COLUMN project_id UUID NULL;
ALTER TABLE chatbot_answer ADD CONSTRAINT ck_chatbot_answer_source CHECK (
    (source = 'WEB' AND user_id IS NOT NULL AND project_id IS NULL)
    OR (source = 'JIRA_APP' AND user_id IS NULL AND project_id IS NOT NULL));
--rollback DELETE FROM chatbot_answer WHERE source = 'JIRA_APP';
--rollback ALTER TABLE chatbot_answer DROP CONSTRAINT ck_chatbot_answer_source;
--rollback ALTER TABLE chatbot_answer DROP COLUMN project_id;
--rollback ALTER TABLE chatbot_answer DROP COLUMN source;
--rollback ALTER TABLE chatbot_answer ALTER COLUMN user_id SET NOT NULL;
