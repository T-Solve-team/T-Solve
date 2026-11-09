--liquibase formatted sql

--changeset review:024-review-send-back-decision
ALTER TABLE review_decision DROP CONSTRAINT ck_review_decision;
ALTER TABLE review_decision ADD CONSTRAINT ck_review_decision
    CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'SEND_BACK'));
