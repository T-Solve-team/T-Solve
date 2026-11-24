-- platform AC-001.29, AC-001.31 (KAN-253): the installer. Run by the last, runAlways changeSet of the master, so it
-- reaches the tables of every changeset above it. Replaces tenant_guard on every table on every run.
DO $$
DECLARE
    t text;
BEGIN
    FOR t IN
        SELECT c.relname FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND c.relname NOT IN ('tenant_identity', 'databasechangelog', 'databasechangeloglock')
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_guard ON public.%I', t);
        EXECUTE format(
            'CREATE POLICY tenant_guard ON public.%I AS PERMISSIVE FOR ALL USING (tenant_guard_ok()) WITH CHECK (tenant_guard_ok())',
            t);
    END LOOP;
END
$$;
