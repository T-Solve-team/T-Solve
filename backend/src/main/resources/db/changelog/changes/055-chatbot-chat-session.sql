--liquibase formatted sql

--changeset chatbot:055-chatbot-chat-session
-- KAN-183: a web user's conversations, kept 30 days after their last message (spec STORY-013).
CREATE TABLE chat_session (
    chat_session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id),
    title TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_session_user_started ON chat_session (user_id, started_at DESC);
CREATE INDEX idx_chat_session_last_message ON chat_session (last_message_at);

CREATE TABLE chat_message (
    chat_message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_session_id UUID NOT NULL REFERENCES chat_session(chat_session_id) ON DELETE CASCADE,
    chatbot_answer_id UUID NOT NULL UNIQUE REFERENCES chatbot_answer(chatbot_answer_id),
    question TEXT NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    answer_text TEXT NOT NULL,
    citations JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_message_outcome
        CHECK (outcome IN ('ANSWERED', 'NO_SOURCE', 'GREETING', 'OFF_TOPIC', 'INJECTION'))
);
CREATE INDEX idx_chat_message_session_created ON chat_message (chat_session_id, created_at);

--rollback DROP TABLE chat_message;
--rollback DROP TABLE chat_session;
