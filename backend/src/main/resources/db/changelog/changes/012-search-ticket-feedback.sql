--liquibase formatted sql

--changeset search:012-search-ticket-feedback
CREATE TABLE ticket_feedback (
    ticket_feedback_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id BIGINT NOT NULL REFERENCES ticket(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    rating VARCHAR(20) NOT NULL,
    comment TEXT,
    channel VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ticket_feedback_rating CHECK (rating IN ('HELPFUL', 'NOT_HELPFUL')),
    CONSTRAINT ck_ticket_feedback_comment CHECK (char_length(comment) <= 500),
    CONSTRAINT ck_ticket_feedback_channel CHECK (channel IN ('WEB', 'ISSUE_PANEL')),
    CONSTRAINT uq_ticket_feedback_ticket_user UNIQUE (ticket_id, user_id)
);

--rollback DROP TABLE ticket_feedback;
