package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RowGuardConventionIT {
    private static TenantRowGuardDatabase database;

    @BeforeAll
    static void database() throws Exception {
        database = TenantRowGuardDatabase.create();
    }

    @BeforeEach
    void fixture() throws Exception {
        database.owner.execute("DROP TABLE IF EXISTS guard_fixture_probes");
        database.owner.execute(
                "CREATE TABLE guard_fixture_probes (probe_id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
        database.migrate();
        database.secureIdentity();
        database.seed();
    }

    @Test
    void everyTenantTableIncludingLaterTablesHasExactlyTheGuard_AC_001_29_AC_001_31() {
        assertThat(policyProblems()).isEmpty();
        assertThat(database.owner.queryForList(
                        "SELECT tablename FROM pg_policies WHERE policyname='tenant_guard'", String.class))
                .contains("guard_fixture_probes", "isolation_probes", "ticket", "app_user")
                .doesNotContain("tenant_identity", "databasechangelog", "databasechangeloglock");
    }

    @Test
    void policiesDoNotCompareLegacyWorkspaceColumns_AC_003_10() {
        assertThat(database.owner.queryForList(
                        "SELECT tablename FROM pg_policies WHERE schemaname='public' "
                                + "AND (qual ILIKE '%workspace_id%' OR with_check ILIKE '%workspace_id%')",
                        String.class))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DROP POLICY tenant_guard ON guard_fixture_probes",
                "ALTER POLICY tenant_guard ON guard_fixture_probes RENAME TO different_name",
                "ALTER POLICY tenant_guard ON guard_fixture_probes USING (true) WITH CHECK (true)",
                "CREATE POLICY extra_policy ON guard_fixture_probes FOR ALL USING (true) WITH CHECK (true)",
                "ALTER TABLE guard_fixture_probes DISABLE ROW LEVEL SECURITY"
            })
    void namesTablesWithAMissingOrAlteredPolicy_AC_001_31(String mutation) {
        database.owner.execute(mutation);
        assertThat(policyProblems()).anyMatch(problem -> problem.startsWith("guard_fixture_probes:"));
    }

    @Test
    void runtimeRoleDoesNotOwnProtectedTablesOrBypassRls_AC_001_32() {
        assertThat(roleProblems()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERUSER", "BYPASSRLS"})
    void detectsPrivilegedRuntimeRoles_AC_001_32(String attribute) {
        try {
            database.admin.execute("ALTER ROLE " + database.runtimeName + " " + attribute);
            assertThat(roleProblems()).anyMatch(problem -> problem.contains("bypasses row security"));
        } finally {
            database.admin.execute("ALTER ROLE " + database.runtimeName + " NOSUPERUSER NOBYPASSRLS");
        }
    }

    @Test
    void detectsRuntimeOwnership_AC_001_32() {
        try {
            database.admin.execute("ALTER TABLE guard_fixture_probes OWNER TO " + database.runtimeName);
            assertThat(roleProblems()).anyMatch(problem -> problem.contains("guard_fixture_probes"));
        } finally {
            database.admin.execute("ALTER TABLE guard_fixture_probes OWNER TO " + database.ownerName);
        }
    }

    @Test
    void detectsRuntimeMembershipInTheOwnerRole_AC_001_32() {
        try {
            database.admin.execute("GRANT " + database.ownerName + " TO " + database.runtimeName);
            assertThat(roleProblems()).anyMatch(problem -> problem.contains("can become table owner"));
        } finally {
            database.admin.execute("REVOKE " + database.ownerName + " FROM " + database.runtimeName);
        }
    }

    @Test
    void runtimeCanReadIdentityButCannotWriteIt_AC_001_28() {
        assertThat(identityPermissionProblems()).isEmpty();
        try {
            database.owner.execute("GRANT UPDATE ON tenant_identity TO " + database.runtimeName);
            assertThat(identityPermissionProblems()).contains("runtime can change tenant_identity");
        } finally {
            database.secureIdentity();
        }
    }

    private static List<String> policyProblems() {
        var problems = new ArrayList<String>();
        database.owner.query(
                """
                SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
                       p.policyname, p.cmd, p.permissive, p.qual, p.with_check,
                       p.roles::text AS policy_roles
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid=c.relnamespace
                  LEFT JOIN pg_policies p ON p.schemaname=n.nspname AND p.tablename=c.relname
                 WHERE n.nspname='public' AND c.relkind IN ('r','p')
                   AND c.relname NOT IN ('tenant_identity','databasechangelog','databasechangeloglock')
                 ORDER BY c.relname
                """,
                result -> {
                    String table = result.getString("relname");
                    if (!result.getBoolean("relrowsecurity") || result.getBoolean("relforcerowsecurity")) {
                        problems.add(table + ": invalid RLS flags");
                    }
                    if (!"tenant_guard".equals(result.getString("policyname"))
                            || !"ALL".equals(result.getString("cmd"))
                            || !"PERMISSIVE".equals(result.getString("permissive"))
                            || !"{public}".equals(result.getString("policy_roles"))
                            || !guardExpression(result.getString("qual"))
                            || !guardExpression(result.getString("with_check"))) {
                        problems.add(table + ": missing or altered tenant_guard");
                    }
                });
        return problems;
    }

    private static boolean guardExpression(String expression) {
        return expression != null
                && "tenant_guard_ok()".equals(expression.replace("public.", "").strip());
    }

    private static List<String> roleProblems() {
        var problems = new ArrayList<String>();
        if (Boolean.TRUE.equals(database.owner.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=?",
                Boolean.class,
                database.runtimeName))) {
            problems.add("runtime bypasses row security");
        }
        problems.addAll(database.owner.queryForList(
                """
                SELECT c.relname || ': runtime owns or can become table owner'
                  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  JOIN pg_roles r ON r.rolname=?
                 WHERE n.nspname='public' AND c.relkind IN ('r','p')
                   AND pg_has_role(r.oid,c.relowner,'MEMBER')
                """,
                String.class,
                database.runtimeName));
        return problems;
    }

    private static List<String> identityPermissionProblems() {
        var problems = new ArrayList<String>();
        if (!Boolean.TRUE.equals(database.owner.queryForObject(
                "SELECT has_table_privilege(CAST(? AS name), 'public.tenant_identity', 'SELECT')",
                Boolean.class,
                database.runtimeName))) {
            problems.add("runtime cannot read tenant_identity");
        }
        for (String privilege : List.of("INSERT", "UPDATE", "DELETE", "TRUNCATE")) {
            if (Boolean.TRUE.equals(database.owner.queryForObject(
                    "SELECT has_table_privilege(CAST(? AS name), 'public.tenant_identity', ?)",
                    Boolean.class,
                    database.runtimeName,
                    privilege))) {
                problems.add("runtime can change tenant_identity");
            }
        }
        return problems;
    }
}
