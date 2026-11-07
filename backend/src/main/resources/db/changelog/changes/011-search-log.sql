--liquibase formatted sql

--changeset search:011-search-log
CREATE TABLE search_log (
    search_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id),
    department_id BIGINT REFERENCES department(department_id),
    query TEXT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    result_count INTEGER NOT NULL,
    is_fallback BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_search_log_channel CHECK (channel IN ('WEB', 'ISSUE_PANEL', 'CHATBOT')),
    CONSTRAINT ck_search_log_result_count CHECK (result_count >= 0)
);

CREATE INDEX idx_search_log_created_at ON search_log (created_at);

--rollback DROP TABLE search_log;
