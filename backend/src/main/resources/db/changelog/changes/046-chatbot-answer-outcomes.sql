--liquibase formatted sql

--changeset chatbot:046-chatbot-answer-outcomes
-- KAN-243: greetings and refused questions get their own outcome, so the knowledge gaps (NO_SOURCE) leave them out.
ALTER TABLE chatbot_answer DROP CONSTRAINT ck_chatbot_answer_outcome;
ALTER TABLE chatbot_answer ADD CONSTRAINT ck_chatbot_answer_outcome
    CHECK (outcome IN ('ANSWERED', 'NO_SOURCE', 'GREETING', 'OFF_TOPIC', 'INJECTION'));

-- The rollback fails while GREETING, OFF_TOPIC or INJECTION rows exist: delete them first.
--rollback ALTER TABLE chatbot_answer DROP CONSTRAINT ck_chatbot_answer_outcome;
--rollback ALTER TABLE chatbot_answer ADD CONSTRAINT ck_chatbot_answer_outcome CHECK (outcome IN ('ANSWERED', 'NO_SOURCE'));
