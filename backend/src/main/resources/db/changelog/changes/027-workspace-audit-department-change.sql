--liquibase formatted sql

--changeset workspace:027-workspace-audit-department-change
ALTER TABLE audit_log_entry DROP CONSTRAINT ck_audit_log_entry_action;
ALTER TABLE audit_log_entry ADD CONSTRAINT ck_audit_log_entry_action CHECK (action IN (
    'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'PUBLISH', 'UNPUBLISH', 'IMPORT', 'DELETE',
    'ROLE_CHANGE', 'PERMISSION_CHANGE', 'TRACKER_CONFIGURATION_CHANGE', 'DEPARTMENT_CHANGE'
));

--rollback ALTER TABLE audit_log_entry DROP CONSTRAINT ck_audit_log_entry_action;
--rollback ALTER TABLE audit_log_entry ADD CONSTRAINT ck_audit_log_entry_action CHECK (action IN ('APPROVE', 'REJECT', 'REQUEST_CHANGES', 'PUBLISH', 'UNPUBLISH', 'IMPORT', 'DELETE', 'ROLE_CHANGE', 'PERMISSION_CHANGE', 'TRACKER_CONFIGURATION_CHANGE', 'DEPARTMENT_CHANGE'));
