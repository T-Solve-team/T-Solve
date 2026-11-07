--liquibase formatted sql

--changeset chatbot:013-chatbot-answer
CREATE TABLE chatbot_answer (
    chatbot_answer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id),
    question TEXT NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    answer_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chatbot_answer_outcome CHECK (outcome IN ('ANSWERED', 'NO_SOURCE'))
);

--rollback DROP TABLE chatbot_answer;
