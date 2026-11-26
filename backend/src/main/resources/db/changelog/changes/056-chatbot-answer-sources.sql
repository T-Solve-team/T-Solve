--liquibase formatted sql

--changeset chatbot:056-chatbot-answer-sources
-- KAN-463: the kept sources of each answer, ids and scores only, for debugging retrieval (spec STORY-014).
ALTER TABLE chatbot_answer ADD COLUMN sources JSONB NOT NULL DEFAULT '[]'::jsonb;
--rollback ALTER TABLE chatbot_answer DROP COLUMN sources;
