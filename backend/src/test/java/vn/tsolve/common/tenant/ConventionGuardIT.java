package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import vn.tsolve.support.PostgresTestBase;

/**
 * The convention guard of the tenant changelog (KAN-253, Task 5): the real changelog is applied by the owner to a fresh
 * database and the checks read {@code information_schema}, {@code pg_catalog} and {@code pg_policies}. The checkers are
 * static methods so that scratch tables, created in a rolled-back transaction, can prove that they fail.
 */
class ConventionGuardIT {

    /** Permanent: the two Liquibase tables and {@code tenant_identity} (AC-001.28). */
    static final Set<String> ALLOW_LIST = Set.of("databasechangelog", "databasechangeloglock", "tenant_identity");

    /** Fixed set of AC-001.21 (as changed 23/11). Modules take their tables off; nobody adds one. */
    static final Set<String> LEGACY_ALLOW_LIST = Set.of(
            "legacy_workspace",
            "workspace",
            "department",
            "app_user",
            "user_role",
            "space",
            "jira_account_links",
            "import_batch",
            "import_preview",
            "import_preview_row",
            "project",
            "suggestion_result");

    private static final Set<String> FIXED_LEGACY_SET = Set.of(
            "legacy_workspace",
            "workspace",
            "department",
            "app_user",
            "user_role",
            "space",
            "jira_account_links",
            "import_batch",
            "import_preview",
            "import_preview_row",
            "project",
            "suggestion_result");

    private static final Set<String> NOT_GUARDED =
            Set.of("tenant_identity", "databasechangelog", "databasechangeloglock");
    private static final String GUARD = "tenant_guard_ok()";
    private static final String CATALOG_MASTER = "db/changelog/catalog/catalog-master.xml";

    private static TenantGuardDatabase db;

    @BeforeAll
    static void applyTenantChangelog() throws Exception {
        db = TenantGuardDatabase.create("conv").migrate(TenantGuardDatabase.MASTER);
    }

    // ---------------------------------------------------------------- the tests

    @Test
    @DisplayName("AC-001.18 platform's tenant changesets are in the tenant changelog only")
    void platformChangesetsAreInTheTenantChangelogOnly_AC_001_18() throws Exception {
        ChangelogFacts tenant = ChangelogFacts.read(TenantGuardDatabase.MASTER);
        ChangelogFacts catalog = ChangelogFacts.read(CATALOG_MASTER);

        assertThat(tenant.includes)
                .contains("changes/051-platform-tenant-identity.sql", "changes/052-platform-tenant-guard-function.sql");
        assertThat(tenant.changeSets)
                .contains(
                        "platform:051-platform-tenant-identity",
                        "platform:052-platform-tenant-guard-function",
                        "platform:053-platform-tenant-guard-policies");
        assertThat(tenant.lastElementChangeSet)
                .as("the installer is the last element of the master")
                .isEqualTo("platform:053-platform-tenant-guard-policies");
        assertThat(tenant.installerAttributes)
                .containsEntry("runAlways", "true")
                .containsEntry("runOnChange", "true")
                .containsEntry("runOrder", "last");

        assertThat(catalog.changeSets.stream().filter(id -> id.startsWith("platform:05")))
                .as("no tenant guard changeset in the catalog changelog")
                .isEmpty();
        assertThat(catalog.includes.stream()
                        .filter(file -> file.contains("tenant-guard") || file.contains("tenant-identity")))
                .isEmpty();
        Set<String> both = new TreeSet<>(tenant.changeSets);
        both.retainAll(catalog.changeSets);
        assertThat(both).as("an id/author pair in both changelogs").isEmpty();
    }

    @Test
    @DisplayName("AC-001.29 AC-001.31 every tenant table has RLS on and exactly one permissive FOR ALL policy"
            + " tenant_guard with USING and WITH CHECK tenant_guard_ok()")
    void everyTenantTableHasTheGuardPolicy_AC_001_29_AC_001_31() throws Exception {
        try (Connection owner = db.owner()) {
            Map<String, String> violations = guardViolations(owner);
            Set<String> checked = new TreeSet<>(tables(owner));
            checked.removeAll(NOT_GUARDED);

            assertThat(checked).as("tables checked").isNotEmpty().containsAll(LEGACY_ALLOW_LIST);
            assertThat(violations).as("tables without the guard policy").isEmpty();
        }
    }

    @Test
    @DisplayName("AC-001.31 edge 8 the guard check names a table with no policy, a policy named otherwise,"
            + " another condition, or a second policy")
    void guardCheckNamesBrokenTables_AC_001_31_edge_8() throws Exception {
        try (Connection owner = db.owner()) {
            owner.setAutoCommit(false);
            try (Statement statement = owner.createStatement()) {
                for (String table : List.of(
                        "guard_scratch_none", "guard_scratch_renamed", "guard_scratch_other", "guard_scratch_two")) {
                    statement.execute("CREATE TABLE " + table + " (" + table + "_id UUID PRIMARY KEY)");
                }
                for (String table : List.of("guard_scratch_renamed", "guard_scratch_other", "guard_scratch_two")) {
                    statement.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
                }
                statement.execute(
                        "CREATE POLICY other ON guard_scratch_renamed FOR ALL USING (true) WITH CHECK (true)");
                statement.execute(
                        "CREATE POLICY tenant_guard ON guard_scratch_other FOR ALL USING (true) WITH CHECK (true)");
                statement.execute(
                        "CREATE POLICY tenant_guard ON guard_scratch_two FOR ALL USING (true) WITH CHECK (true)");
                statement.execute("CREATE POLICY second ON guard_scratch_two FOR ALL USING (true) WITH CHECK (true)");

                Map<String, String> violations = guardViolations(owner);

                assertThat(violations.keySet())
                        .as("exactly the four scratch tables, no real table: %s", violations)
                        .containsExactlyInAnyOrder(
                                "guard_scratch_none",
                                "guard_scratch_renamed",
                                "guard_scratch_other",
                                "guard_scratch_two");
                assertThat(violations.get("guard_scratch_none")).contains("no policy");
                assertThat(violations.get("guard_scratch_renamed")).contains("other");
                assertThat(violations.get("guard_scratch_other")).contains("condition");
                assertThat(violations.get("guard_scratch_two")).contains("2 policies");
            } finally {
                owner.rollback();
            }
        }
    }

    @Test
    @DisplayName(
            "edge 8 the installer adds tenant_guard to a table a later changeset creates and replaces it on every run")
    void installerRunsLastAndReplacesThePolicy_edge_8() throws Exception {
        TenantGuardDatabase fresh = TenantGuardDatabase.create("order").migrate("db/changelog/guard-order-test.xml");

        try (Connection owner = fresh.owner()) {
            assertThat(policyNames(owner, "guard_order_probe"))
                    .as("the table of a later changeset has the policy after the first update")
                    .containsExactly("tenant_guard");
            assertThat(guardViolations(owner)).isEmpty();
        }

        fresh.migrate("db/changelog/guard-order-test.xml");

        try (Connection owner = fresh.owner()) {
            assertThat(policyNames(owner, "guard_order_probe"))
                    .as("replaced, not duplicated, on the second run")
                    .containsExactly("tenant_guard");
            assertThat(guardViolations(owner)).isEmpty();
        }
    }

    @Test
    @DisplayName("AC-001.20 AC-001.22 the convention check names each table not on the allow-list that breaks"
            + " AC-001.20, with the missing part")
    void conventionCheckNamesEachBrokenTable_AC_001_20_AC_001_22() throws Exception {
        try (Connection owner = db.owner()) {
            Set<String> existing = new HashSet<>(tables(owner));
            owner.setAutoCommit(false);
            try (Statement statement = owner.createStatement()) {
                String ok = " TIMESTAMPTZ NOT NULL DEFAULT now()";
                String key = "_id UUID PRIMARY KEY DEFAULT gen_random_uuid()";
                statement.execute("CREATE TABLE scratch_orders (scratch_orders" + key + ", created_at" + ok
                        + ", updated_at" + ok + ")");
                statement.execute(
                        "CREATE TABLE scratch_idkey (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at" + ok
                                + ", updated_at" + ok + ")");
                statement.execute("CREATE TABLE scratch_bigkey (scratch_bigkey_id BIGSERIAL PRIMARY KEY, created_at"
                        + ok + ", updated_at" + ok + ")");
                statement.execute("CREATE TABLE scratch_wsid (scratch_wsid" + key + ", workspace_id UUID, created_at"
                        + ok + ", updated_at" + ok + ")");
                statement.execute("CREATE TABLE scratch_naive_ts (scratch_naive_ts" + key + ", created_at" + ok
                        + ", updated_at TIMESTAMP NOT NULL DEFAULT now())");
                statement.execute("CREATE TABLE scratch_append_updated (scratch_append_updated" + key + ", created_at"
                        + ok + ", updated_at" + ok + ")");
                statement.execute("COMMENT ON TABLE scratch_append_updated IS 'tsolve:append-only'");
                statement.execute(
                        "CREATE TABLE scratch_append_only (scratch_append_only" + key + ", created_at" + ok + ")");
                statement.execute("COMMENT ON TABLE scratch_append_only IS 'tsolve:append-only'");
                statement.execute(
                        "CREATE TABLE scratch_member (left_id UUID NOT NULL, right_id UUID NOT NULL, created_at" + ok
                                + ", PRIMARY KEY (left_id, right_id))");
                statement.execute(
                        "CREATE TABLE scratch_ok (scratch_ok" + key + ", created_at" + ok + ", updated_at" + ok + ")");

                Map<String, List<String>> violations = conventionViolations(owner, existing);

                assertThat(violations.keySet())
                        .containsExactlyInAnyOrder(
                                "scratch_orders",
                                "scratch_idkey",
                                "scratch_bigkey",
                                "scratch_wsid",
                                "scratch_naive_ts",
                                "scratch_append_updated");
                assertThat(violations.get("scratch_orders")).anyMatch(part -> part.contains("singular"));
                assertThat(violations.get("scratch_idkey")).anyMatch(part -> part.contains("scratch_idkey_id"));
                assertThat(violations.get("scratch_bigkey")).anyMatch(part -> part.contains("UUID"));
                assertThat(violations.get("scratch_wsid")).anyMatch(part -> part.contains("workspace_id"));
                assertThat(violations.get("scratch_naive_ts")).anyMatch(part -> part.contains("TIMESTAMPTZ"));
                assertThat(violations.get("scratch_append_updated")).anyMatch(part -> part.contains("updated_at"));
            } finally {
                owner.rollback();
            }
        }

        // The real changelog: no table outside the allow-lists breaks AC-001.20.
        try (Connection owner = db.owner()) {
            Set<String> exempt = new HashSet<>(ALLOW_LIST);
            exempt.addAll(LEGACY_ALLOW_LIST);
            assertThat(conventionViolations(owner, exempt))
                    .as("tables that are not on the allow-list and break AC-001.20")
                    .isEmpty();
            // KAN-253 Q6-A: audit_log_entry has no updated_at because it is append-only; its comment says so
            try (Statement statement = owner.createStatement();
                    ResultSet row = statement.executeQuery(
                            "SELECT obj_description('public.audit_log_entry'::regclass, 'pg_class')")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1))
                        .as("COMMENT ON TABLE audit_log_entry")
                        .isEqualTo("tsolve:append-only");
            }
        }
    }

    @Test
    @DisplayName("AC-001.21 AC-001.22 edge 12 the allow-list only shrinks")
    void allowListOnlyShrinks_AC_001_21_AC_001_22_edge_12() throws Exception {
        assertThat(ALLOW_LIST)
                .containsExactlyInAnyOrder("databasechangelog", "databasechangeloglock", "tenant_identity");
        assertThat(FIXED_LEGACY_SET)
                .as("an entry was added to the legacy allow-list")
                .containsAll(LEGACY_ALLOW_LIST);

        try (Connection owner = db.owner()) {
            Set<String> existing = new HashSet<>(tables(owner));
            Map<String, List<String>> all = conventionViolations(owner, Set.of());
            assertThat(staleEntries(LEGACY_ALLOW_LIST, existing, all))
                    .as("the allow-list only shrinks")
                    .isEmpty();

            // the check itself: a table that meets AC-001.20, and a name that is no table, both fail
            owner.setAutoCommit(false);
            try (Statement statement = owner.createStatement()) {
                statement.execute(
                        "CREATE TABLE scratch_fine (scratch_fine_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                                + "created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
                Set<String> nowExisting = new HashSet<>(tables(owner));
                Map<String, List<String>> now = conventionViolations(owner, Set.of());

                List<String> stale = staleEntries(Set.of("scratch_fine", "no_such_table"), nowExisting, now);

                assertThat(stale).hasSize(2);
                assertThat(stale).allMatch(message -> message.contains("the allow-list only shrinks"));
                assertThat(stale).anyMatch(message -> message.startsWith("scratch_fine"));
                assertThat(stale).anyMatch(message -> message.startsWith("no_such_table"));
            } finally {
                owner.rollback();
            }
        }
    }

    @Test
    @DisplayName("AC-001.23 every tenant table's <table>_id has DEFAULT gen_random_uuid() and its entity uses"
            + " GenerationType.UUID")
    void keysAreGeneratedByTheDatabaseAndByJpa_AC_001_23() throws Exception {
        Set<String> exempt = new HashSet<>(ALLOW_LIST);
        exempt.addAll(LEGACY_ALLOW_LIST);
        List<String> problems = new ArrayList<>();
        Set<String> checked = new TreeSet<>();

        try (Connection owner = db.owner()) {
            Map<String, TableFacts> facts = TableFacts.read(owner);
            for (TableFacts table : facts.values()) {
                if (exempt.contains(table.name) || table.isJoinTable()) {
                    continue;
                }
                checked.add(table.name);
                String key = table.name + "_id";
                String keyDefault = table.columns.containsKey(key) ? table.columns.get(key).columnDefault : null;
                if (keyDefault == null || !keyDefault.contains("gen_random_uuid()")) {
                    problems.add(table.name + ": " + key + " has no DEFAULT gen_random_uuid()");
                }
            }

            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            Set<BeanDefinition> entities = scanner.findCandidateComponents("vn.tsolve");
            assertThat(entities).as("@Entity classes found under vn.tsolve").isNotEmpty();
            for (BeanDefinition definition : entities) {
                Class<?> type = Class.forName(definition.getBeanClassName());
                Table mapped = type.getAnnotation(Table.class);
                String table = mapped != null && !mapped.name().isEmpty() ? mapped.name() : snake(type.getSimpleName());
                if (exempt.contains(table)
                        || !facts.containsKey(table)
                        || facts.get(table).isJoinTable()) {
                    continue;
                }
                Field id = idField(type);
                if (id == null) {
                    continue; // composite or embedded key: not a <table>_id entity
                }
                GeneratedValue generated = id.getAnnotation(GeneratedValue.class);
                if (generated == null || generated.strategy() != GenerationType.UUID) {
                    problems.add(type.getName() + " (table " + table + "): @Id " + id.getName()
                            + " has no @GeneratedValue(strategy = GenerationType.UUID)");
                }
            }
        }

        assertThat(checked).as("tables checked").isNotEmpty();
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("AC-001.32 edge 7 the runtime, Registry and Jobs users are neither superuser nor BYPASSRLS"
            + " and own no table that tenant_guard protects")
    void usersAreNotPrivilegedAndOwnNoProtectedTable_AC_001_32_edge_7() throws Exception {
        List<String> users = List.of(PostgresTestBase.REGISTRY_USER, PostgresTestBase.JOBS_USER, db.runtimeRole);
        try (Connection owner = db.owner();
                Statement statement = owner.createStatement()) {
            for (String user : users) {
                try (ResultSet row = statement.executeQuery(
                        "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = '" + user + "'")) {
                    assertThat(row.next()).as("role " + user + " exists").isTrue();
                    assertThat(row.getBoolean(1)).as(user + " is superuser").isFalse();
                    assertThat(row.getBoolean(2)).as(user + " has BYPASSRLS").isFalse();
                }
            }
            Set<String> protectedTables = new TreeSet<>();
            try (ResultSet rows = statement.executeQuery(
                    "SELECT DISTINCT tablename FROM pg_policies WHERE schemaname = 'public' AND policyname = 'tenant_guard'")) {
                while (rows.next()) {
                    protectedTables.add(rows.getString(1));
                }
            }
            assertThat(protectedTables).as("tables that tenant_guard protects").isNotEmpty();
            Map<String, String> owners = new TreeMap<>();
            try (ResultSet rows =
                    statement.executeQuery("SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'public'")) {
                while (rows.next()) {
                    if (protectedTables.contains(rows.getString(1)) && users.contains(rows.getString(2))) {
                        owners.put(rows.getString(1), rows.getString(2));
                    }
                }
            }
            assertThat(owners)
                    .as("protected tables owned by one of those users")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("AC-003.10 no row policy compares a workspace_id column; the only policy is tenant_guard")
    void noPolicyComparesWorkspaceId_AC_003_10() throws Exception {
        try (Connection owner = db.owner();
                Statement statement = owner.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT tablename, policyname, qual, with_check FROM pg_policies WHERE schemaname = 'public'")) {
            int count = 0;
            List<String> wrong = new ArrayList<>();
            while (rows.next()) {
                count++;
                String condition = rows.getString(3) + " " + rows.getString(4);
                if (!"tenant_guard".equals(rows.getString(2)) || condition.contains("workspace_id")) {
                    wrong.add(rows.getString(1) + ": " + rows.getString(2) + " " + condition);
                }
            }
            assertThat(count).as("policies in the tenant changelog").isPositive();
            assertThat(wrong).isEmpty();
        }
    }

    // ---------------------------------------------------------------- the checkers

    /** Table to reason, for every table that needs the policy of AC-001.29 and has not got exactly the right one. */
    static Map<String, String> guardViolations(Connection connection) throws SQLException {
        Map<String, String> result = new TreeMap<>();
        Map<String, Boolean> rls = new TreeMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT c.relname, c.relrowsecurity FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')")) {
            while (rows.next()) {
                if (!NOT_GUARDED.contains(rows.getString(1))) {
                    rls.put(rows.getString(1), rows.getBoolean(2));
                }
            }
        }
        Map<String, List<String[]>> policies = new TreeMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT tablename, policyname, permissive, cmd, qual, with_check FROM pg_policies "
                                + "WHERE schemaname = 'public'")) {
            while (rows.next()) {
                policies.computeIfAbsent(rows.getString(1), key -> new ArrayList<>())
                        .add(new String[] {
                            rows.getString(2),
                            rows.getString(3),
                            rows.getString(4),
                            rows.getString(5),
                            rows.getString(6)
                        });
            }
        }
        for (Map.Entry<String, Boolean> table : rls.entrySet()) {
            List<String[]> own = policies.getOrDefault(table.getKey(), List.of());
            String reason = null;
            if (own.isEmpty()) {
                reason = "no policy tenant_guard";
            } else if (own.size() > 1) {
                reason = own.size() + " policies, expected exactly one tenant_guard";
            } else if (!"tenant_guard".equals(own.get(0)[0])) {
                reason = "the policy is named " + own.get(0)[0] + ", not tenant_guard";
            } else if (!"PERMISSIVE".equals(own.get(0)[1]) || !"ALL".equals(own.get(0)[2])) {
                reason = "the policy is " + own.get(0)[1] + " FOR " + own.get(0)[2] + ", not PERMISSIVE FOR ALL";
            } else if (!isGuardCondition(own.get(0)[3]) || !isGuardCondition(own.get(0)[4])) {
                reason = "the condition is USING " + own.get(0)[3] + " WITH CHECK " + own.get(0)[4] + ", not " + GUARD;
            } else if (!table.getValue()) {
                reason = "row-level security is not enabled";
            }
            if (reason != null) {
                result.put(table.getKey(), reason);
            }
        }
        return result;
    }

    private static boolean isGuardCondition(String condition) {
        if (condition == null) {
            return false;
        }
        String normal = condition.replace("public.", "").replaceAll("\\s+", "");
        while (normal.startsWith("(") && normal.endsWith(")") && !normal.equals(GUARD)) {
            normal = normal.substring(1, normal.length() - 1);
        }
        return normal.equals(GUARD);
    }

    /** Table to the parts of AC-001.20 it breaks, for every public table not in {@code exempt}. */
    static Map<String, List<String>> conventionViolations(Connection connection, Set<String> exempt)
            throws SQLException {
        Map<String, List<String>> result = new TreeMap<>();
        for (TableFacts table : TableFacts.read(connection).values()) {
            if (exempt.contains(table.name) || ALLOW_LIST.contains(table.name)) {
                continue;
            }
            List<String> parts = new ArrayList<>();
            if (!table.name.matches("[a-z][a-z0-9]*(_[a-z0-9]+)*") || table.name.matches(".*[^sui]s")) {
                parts.add("the name is not a singular snake_case name");
            }
            if (!table.isJoinTable()) {
                String key = table.name + "_id";
                if (!table.primaryKey.equals(List.of(key))) {
                    parts.add("the primary key is " + table.primaryKey + ", not " + key);
                } else {
                    Column column = table.columns.get(key);
                    if (!"uuid".equals(column.dataType)) {
                        parts.add(key + " is " + column.dataType + ", not UUID");
                    }
                    if (column.columnDefault == null || !column.columnDefault.contains("gen_random_uuid()")) {
                        parts.add(key + " has no DEFAULT gen_random_uuid()");
                    }
                }
            }
            for (Column column : table.columns.values()) {
                boolean ownKey = table.name.equals("workspace") && column.name.equals("workspace_id");
                if (column.name.equals("workspace_id") && !ownKey) {
                    parts.add("a workspace_id column");
                }
                if ((column.name.equals("created_at") || column.name.equals("updated_at"))
                        && (!"timestamp with time zone".equals(column.dataType) || column.nullable)) {
                    parts.add(column.name + " is not TIMESTAMPTZ NOT NULL");
                }
            }
            boolean appendOnly = "tsolve:append-only".equals(table.comment);
            if (appendOnly && table.columns.containsKey("updated_at")) {
                parts.add("an append-only table has an updated_at column");
            }
            if (!parts.isEmpty()) {
                result.put(table.name, parts);
            }
        }
        return result;
    }

    /** Messages for legacy entries that must leave the list: the table is gone or now meets AC-001.20. */
    static List<String> staleEntries(
            Set<String> legacy, Set<String> existing, Map<String, List<String>> violationsOfAllTables) {
        List<String> messages = new ArrayList<>();
        for (String entry : new TreeSet<>(legacy)) {
            if (!existing.contains(entry)) {
                messages.add(entry + " names a table that does not exist; the allow-list only shrinks");
            } else if (!violationsOfAllTables.containsKey(entry)) {
                messages.add(entry + " meets AC-001.20; the allow-list only shrinks");
            }
        }
        return messages;
    }

    // ---------------------------------------------------------------- catalog readers

    static List<String> tables(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT c.relname FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') ORDER BY 1")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
    }

    private static List<String> policyNames(Connection connection, String table) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT policyname FROM pg_policies WHERE schemaname = 'public' AND tablename = '" + table
                                + "'")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
    }

    private static Field idField(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    return field;
                }
            }
        }
        return null;
    }

    private static String snake(String className) {
        return className.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private record Column(String name, String dataType, boolean nullable, String columnDefault) {}

    private static final class TableFacts {
        final String name;
        final Map<String, Column> columns = new LinkedHashMap<>();
        final List<String> primaryKey = new ArrayList<>();
        String comment;

        TableFacts(String name) {
            this.name = name;
        }

        /** Two {@code <entity>_id} columns as the whole primary key (decision X2). */
        boolean isJoinTable() {
            return primaryKey.size() == 2 && primaryKey.stream().allMatch(column -> column.endsWith("_id"));
        }

        static Map<String, TableFacts> read(Connection connection) throws SQLException {
            Map<String, TableFacts> tables = new TreeMap<>();
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rows = statement.executeQuery("SELECT c.relname, obj_description(c.oid, 'pg_class') "
                        + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')")) {
                    while (rows.next()) {
                        TableFacts table = new TableFacts(rows.getString(1));
                        table.comment = rows.getString(2);
                        tables.put(table.name, table);
                    }
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT table_name, column_name, data_type, is_nullable, column_default "
                                + "FROM information_schema.columns WHERE table_schema = 'public' ORDER BY ordinal_position")) {
                    while (rows.next()) {
                        TableFacts table = tables.get(rows.getString(1));
                        if (table != null) {
                            table.columns.put(
                                    rows.getString(2),
                                    new Column(
                                            rows.getString(2),
                                            rows.getString(3),
                                            "YES".equals(rows.getString(4)),
                                            rows.getString(5)));
                        }
                    }
                }
                try (ResultSet rows = statement.executeQuery("SELECT c.relname, a.attname FROM pg_constraint k "
                        + "JOIN pg_class c ON c.oid = k.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "JOIN LATERAL unnest(k.conkey) WITH ORDINALITY AS u(attnum, ord) ON true "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = u.attnum "
                        + "WHERE k.contype = 'p' AND n.nspname = 'public' ORDER BY c.relname, u.ord")) {
                    while (rows.next()) {
                        TableFacts table = tables.get(rows.getString(1));
                        if (table != null) {
                            table.primaryKey.add(rows.getString(2));
                        }
                    }
                }
            }
            return tables;
        }
    }

    /** What a master changelog on the classpath declares: includes, id/author pairs and its last element. */
    private static final class ChangelogFacts {
        final Set<String> includes = new LinkedHashSet<>();
        final Set<String> changeSets = new LinkedHashSet<>();
        final Map<String, String> installerAttributes = new LinkedHashMap<>();
        String lastElementChangeSet;

        static ChangelogFacts read(String master) throws Exception {
            ChangelogFacts facts = new ChangelogFacts();
            String directory = master.substring(0, master.lastIndexOf('/') + 1);
            Document document;
            try (InputStream in = ConventionGuardIT.class.getClassLoader().getResourceAsStream(master)) {
                assertThat(in).as(master).isNotNull();
                document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(in);
            }
            Element last = null;
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) children.item(i);
                last = element;
                if (element.getTagName().equals("include")) {
                    String file = element.getAttribute("file");
                    facts.includes.add(file);
                    try (InputStream sql =
                            ConventionGuardIT.class.getClassLoader().getResourceAsStream(directory + file)) {
                        String text = sql == null ? "" : new String(sql.readAllBytes(), StandardCharsets.UTF_8);
                        Matcher matcher = Pattern.compile("--changeset\\s+([^:\\s]+):(\\S+)")
                                .matcher(text);
                        while (matcher.find()) {
                            facts.changeSets.add(matcher.group(1) + ":" + matcher.group(2));
                        }
                    }
                } else if (element.getTagName().equals("changeSet")) {
                    String pair = element.getAttribute("author") + ":" + element.getAttribute("id");
                    facts.changeSets.add(pair);
                    if (pair.equals("platform:053-platform-tenant-guard-policies")) {
                        for (String attribute : List.of("runAlways", "runOnChange", "runOrder")) {
                            facts.installerAttributes.put(attribute, element.getAttribute(attribute));
                        }
                    }
                }
            }
            if (last != null && last.getTagName().equals("changeSet")) {
                facts.lastElementChangeSet = last.getAttribute("author") + ":" + last.getAttribute("id");
            }
            return facts;
        }
    }
}
