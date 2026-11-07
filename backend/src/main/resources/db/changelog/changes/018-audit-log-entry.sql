--liquibase formatted sql

--changeset dashboard-audit:018-audit-log-entry
DROP TABLE audit_event;
CREATE TABLE audit_log_entry (
    audit_log_entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID,
    actor_email VARCHAR(254) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_audit_log_entry_action CHECK (action IN (
        'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'PUBLISH', 'UNPUBLISH', 'IMPORT', 'DELETE',
        'ROLE_CHANGE', 'PERMISSION_CHANGE', 'TRACKER_CONFIGURATION_CHANGE'
    ))
);
CREATE INDEX idx_audit_log_entry_created ON audit_log_entry (created_at);

--changeset dashboard-audit:018-audit-log-entry-append-only splitStatements:false
CREATE FUNCTION refuse_audit_log_entry_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log_entry is append-only';
END;
$$;
CREATE TRIGGER audit_log_entry_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log_entry
    FOR EACH ROW EXECUTE FUNCTION refuse_audit_log_entry_mutation();
CREATE TRIGGER audit_log_entry_no_truncate
    BEFORE TRUNCATE ON audit_log_entry
    FOR EACH STATEMENT EXECUTE FUNCTION refuse_audit_log_entry_mutation();
