--liquibase formatted sql

--changeset ticket:037-review-decision-publication
ALTER TABLE review_decision DROP CONSTRAINT ck_review_decision;
ALTER TABLE review_decision ADD CONSTRAINT ck_review_decision
    CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'SEND_BACK', 'UNPUBLISH', 'REPUBLISH'));
--rollback DELETE FROM review_decision WHERE decision IN ('UNPUBLISH', 'REPUBLISH');
--rollback ALTER TABLE review_decision DROP CONSTRAINT ck_review_decision;
--rollback ALTER TABLE review_decision ADD CONSTRAINT ck_review_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'SEND_BACK'));
