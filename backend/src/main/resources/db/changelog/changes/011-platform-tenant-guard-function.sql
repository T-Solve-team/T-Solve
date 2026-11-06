--liquibase formatted sql

--changeset platform:011-platform-tenant-guard-function splitStatements:false
CREATE FUNCTION public.tenant_guard_ok() RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT NULLIF(pg_catalog.current_setting('app.tenant_id', true), '') =
           (SELECT tenant_id::text FROM public.tenant_identity)
$$;

-- Text comparison fails closed for malformed settings too; no UUID cast can throw before policy evaluation.
--rollback DROP FUNCTION public.tenant_guard_ok();
