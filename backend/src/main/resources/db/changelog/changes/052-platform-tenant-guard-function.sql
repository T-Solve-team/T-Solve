--liquibase formatted sql

--changeset platform:052-platform-tenant-guard-function splitStatements:false

-- platform AC-001.29, AC-001.30 (KAN-253): true only when app.tenant_id equals the row of public.tenant_identity.
-- Unset, empty, another id or no identity row gives false (fail closed); a malformed id raises 22P02. The table is read
-- by its full name so a temporary table cannot shadow it.
CREATE OR REPLACE FUNCTION tenant_guard_ok() RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.tenant_identity
        WHERE tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    )
$$;

--rollback DROP FUNCTION tenant_guard_ok();
