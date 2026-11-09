--liquibase formatted sql

--changeset chatbot:024-chatbot-answer-rating
ALTER TABLE chatbot_answer
    ADD COLUMN rating VARCHAR(20) NULL,
    ADD COLUMN rated_at TIMESTAMPTZ NULL;

ALTER TABLE chatbot_answer
    ADD CONSTRAINT ck_chatbot_answer_rating CHECK (rating IN ('HELPFUL', 'NOT_HELPFUL')),
    ADD CONSTRAINT ck_chatbot_answer_rated CHECK ((rating IS NULL) = (rated_at IS NULL));

--rollback ALTER TABLE chatbot_answer DROP CONSTRAINT ck_chatbot_answer_rating, DROP CONSTRAINT ck_chatbot_answer_rated;
--rollback ALTER TABLE chatbot_answer DROP COLUMN rating, DROP COLUMN rated_at;
