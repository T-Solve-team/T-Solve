--liquibase formatted sql

--changeset platform:054-platform-audit-log-entry-append-only

-- platform AC-001.20 (KAN-253): audit_log_entry is append-only, so it has no updated_at; the comment says so.
COMMENT ON TABLE audit_log_entry IS 'tsolve:append-only';

--rollback COMMENT ON TABLE audit_log_entry IS NULL;
