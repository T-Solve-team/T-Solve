-- Invoked by the XML changeset: formatted SQL does not support runOrder (BUG-128).
DO $$
DECLARE
    tenant_table RECORD;
BEGIN
    FOR tenant_table IN
        SELECT tablename FROM pg_catalog.pg_tables
         WHERE schemaname = 'public'
           AND tablename NOT IN ('tenant_identity', 'databasechangelog', 'databasechangeloglock')
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tenant_table.tablename);
        EXECUTE format('DROP POLICY IF EXISTS tenant_guard ON public.%I', tenant_table.tablename);
        EXECUTE format(
            'CREATE POLICY tenant_guard ON public.%I FOR ALL TO PUBLIC '
            'USING (public.tenant_guard_ok()) WITH CHECK (public.tenant_guard_ok())',
            tenant_table.tablename
        );
    END LOOP;
END;
$$;
