--liquibase formatted sql

--changeset analytics:027-dashboard-period-indexes
CREATE INDEX idx_review_decision_decided_at ON review_decision (decided_at);
CREATE INDEX idx_ticket_created_at ON ticket (created_at);
CREATE INDEX idx_chatbot_answer_created_at ON chatbot_answer (created_at);
CREATE INDEX idx_ticket_feedback_updated_at ON ticket_feedback (updated_at);
CREATE INDEX idx_suggestion_result_commented_at ON suggestion_result (commented_at);

--rollback DROP INDEX idx_suggestion_result_commented_at;
--rollback DROP INDEX idx_ticket_feedback_updated_at;
--rollback DROP INDEX idx_chatbot_answer_created_at;
--rollback DROP INDEX idx_ticket_created_at;
--rollback DROP INDEX idx_review_decision_decided_at;
