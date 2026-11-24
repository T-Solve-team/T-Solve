package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The row guard at work (KAN-253, Task 5): one tenant database whose {@code tenant_identity} is workspace A, the test-only
 * table {@code isolation_probes} with one row, and a runtime user that is neither the owner nor BYPASSRLS and has never
 * been granted TRUNCATE ({@link TenantGuardDatabase}). The runtime user's connections are plain JDBC.
 */
class RowGuardIT {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String INVALID_TEXT_REPRESENTATION = "22P02";

    private static TenantGuardDatabase db;

    @BeforeAll
    static void createFixture() throws Exception {
        db = TenantGuardDatabase.ready("row", A);
    }

    @Test
    @DisplayName("AC-001.28 edge 10 edge 6 tenant_identity holds at most one row, the runtime user reads it and cannot"
            + " change it, and with no row nothing is visible")
    void identityIsOneRowReadableButNotWritable_AC_001_28_edge_10_edge_6() throws Exception {
        try (Connection owner = db.owner()) {
            assertThat(sqlState(owner, "INSERT INTO tenant_identity (tenant_id) VALUES ('" + B + "')"))
                    .as("a second row")
                    .isEqualTo("23505");
            assertThat(sqlState(
                            owner, "INSERT INTO tenant_identity (singleton, tenant_id) VALUES (false, '" + B + "')"))
                    .as("singleton = false")
                    .isEqualTo("23514");
        }
        try (Connection runtime = db.runtime();
                Statement statement = runtime.createStatement();
                ResultSet rows = statement.executeQuery("SELECT tenant_id FROM tenant_identity")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1, UUID.class)).isEqualTo(A);
            assertThat(rows.next()).isFalse();
        }
        try (Connection runtime = db.runtime()) {
            assertThat(sqlState(runtime, "INSERT INTO tenant_identity (tenant_id) VALUES ('" + B + "')"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(sqlState(runtime, "UPDATE tenant_identity SET tenant_id = '" + B + "'"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(sqlState(runtime, "DELETE FROM tenant_identity")).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(sqlState(runtime, "TRUNCATE tenant_identity")).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }

        // a database whose provisioning died before the identity row was written: fail closed
        TenantGuardDatabase empty = TenantGuardDatabase.ready("norow", null);
        try (Connection runtime = empty.runtime();
                Statement statement = runtime.createStatement()) {
            statement.execute("SELECT set_config('app.tenant_id', '" + A + "', false)");
            assertThat(count(runtime, "SELECT count(*) FROM isolation_probes"))
                    .as("rows visible with no tenant_identity row")
                    .isZero();
            assertThat(sqlState(runtime, "INSERT INTO isolation_probes (note) VALUES ('x')"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    static Stream<Arguments> unmatchedSettings() {
        return Stream.of(
                Arguments.of("unset", new String[] {}),
                Arguments.of("empty string", new String[] {"SELECT set_config('app.tenant_id', '', false)"}),
                Arguments.of("set then reset", new String[] {"SET app.tenant_id = '" + A + "'", "RESET app.tenant_id"}),
                Arguments.of(
                        "another Workspace's id",
                        new String[] {"SELECT set_config('app.tenant_id', '" + B + "', false)"}),
                Arguments.of("malformed", new String[] {"SELECT set_config('app.tenant_id', 'not-a-uuid', false)"}),
                Arguments.of("control: tenant_identity's own id", new String[] {}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unmatchedSettings")
    @DisplayName("AC-001.27 AC-001.30 AC-001.29 edge 9 the runtime user sees and changes nothing unless app.tenant_id"
            + " equals tenant_identity")
    void nothingIsVisibleUnlessTheSettingMatches_AC_001_27_AC_001_30_AC_001_29_edge_9(String name, String[] setup)
            throws Exception {
        if (name.startsWith("control")) {
            matchingSettingSeesAndChangesRows();
            return;
        }
        boolean malformed = name.equals("malformed");
        try (Connection runtime = db.runtime();
                Statement statement = runtime.createStatement()) {
            for (String sql : setup) {
                statement.execute(sql);
            }
            String read = stateOrCount(runtime, "SELECT count(*) FROM isolation_probes");
            assertThat(malformed ? zeroOrInvalidText(read) : read).as("read").isEqualTo(malformed ? "ok" : "0");
            String insert = sqlState(runtime, "INSERT INTO isolation_probes (note) VALUES ('intruder')");
            assertThat(malformed ? oneOf(insert, INSUFFICIENT_PRIVILEGE, INVALID_TEXT_REPRESENTATION) : insert)
                    .as("insert")
                    .isEqualTo(malformed ? "ok" : INSUFFICIENT_PRIVILEGE);
            String update = stateOrUpdateCount(runtime, "UPDATE isolation_probes SET note = 'changed'");
            assertThat(malformed ? zeroOrInvalidText(update) : update)
                    .as("update")
                    .isEqualTo(malformed ? "ok" : "0");
            String delete = stateOrUpdateCount(runtime, "DELETE FROM isolation_probes");
            assertThat(malformed ? zeroOrInvalidText(delete) : delete)
                    .as("delete")
                    .isEqualTo(malformed ? "ok" : "0");
        }
        try (Connection owner = db.owner();
                Statement statement = owner.createStatement();
                ResultSet rows = statement.executeQuery("SELECT note FROM isolation_probes")) {
            assertThat(rows.next()).as("the owner still sees the row").isTrue();
            assertThat(rows.getString(1)).isEqualTo("probe");
            assertThat(rows.next()).as("and no other row").isFalse();
        }
    }

    /** The control case: with app.tenant_id equal to tenant_identity the runtime user reads and writes. */
    private static void matchingSettingSeesAndChangesRows() throws Exception {
        try (Connection runtime = db.runtime()) {
            runtime.setAutoCommit(false);
            try (Statement statement = runtime.createStatement()) {
                statement.execute("SELECT set_config('app.tenant_id', '" + A + "', true)");
                assertThat(count(runtime, "SELECT count(*) FROM isolation_probes"))
                        .isEqualTo(1);
                assertThat(statement.executeUpdate("INSERT INTO isolation_probes (note) VALUES ('mine')"))
                        .isEqualTo(1);
                assertThat(statement.executeUpdate("UPDATE isolation_probes SET note = 'changed' WHERE note = 'mine'"))
                        .isEqualTo(1);
                assertThat(statement.executeUpdate("DELETE FROM isolation_probes WHERE note = 'changed'"))
                        .isEqualTo(1);
            } finally {
                runtime.rollback();
            }
        }
    }

    @Test
    @DisplayName("edge 6 the runtime user cannot disable RLS, drop or replace tenant_guard, or shadow tenant_identity")
    void runtimeUserCannotRemoveTheGuard_edge_6() throws Exception {
        String functionBefore;
        try (Connection owner = db.owner();
                Statement statement = owner.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT relrowsecurity FROM pg_class WHERE relname = 'isolation_probes' AND relkind = 'r'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1))
                        .as("RLS is on for isolation_probes")
                        .isTrue();
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT count(*) FROM pg_policies WHERE tablename = 'isolation_probes' AND policyname = 'tenant_guard'")) {
                rows.next();
                assertThat(rows.getInt(1))
                        .as("tenant_guard exists on isolation_probes")
                        .isEqualTo(1);
            }
            functionBefore = functionSource(owner);
        }

        try (Connection runtime = db.runtime()) {
            assertThat(sqlState(runtime, "ALTER TABLE isolation_probes DISABLE ROW LEVEL SECURITY"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(sqlState(runtime, "DROP POLICY tenant_guard ON isolation_probes"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(sqlState(runtime, "CREATE POLICY extra ON isolation_probes USING (true)"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(sqlState(runtime, "ALTER POLICY tenant_guard ON isolation_probes USING (true)"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(
                            sqlState(
                                    runtime,
                                    "CREATE OR REPLACE FUNCTION tenant_guard_ok() RETURNS boolean LANGUAGE sql AS 'SELECT true'"))
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
        }

        // Q5: even a role that may create temporary tables cannot shadow tenant_identity
        try (Connection superuser = db.superuser();
                Statement statement = superuser.createStatement()) {
            statement.execute("GRANT TEMP ON DATABASE \"" + db.database + "\" TO \"" + db.runtimeRole + "\"");
        }
        try {
            try (Connection runtime = db.runtime();
                    Statement statement = runtime.createStatement()) {
                statement.execute("CREATE TEMP TABLE tenant_identity (singleton BOOLEAN, tenant_id UUID)");
                statement.execute("INSERT INTO pg_temp.tenant_identity VALUES (true, '" + B + "')");
                statement.execute("SET search_path = pg_temp, public");
                statement.execute("SELECT set_config('app.tenant_id', '" + B + "', false)");
                assertThat(count(runtime, "SELECT count(*) FROM isolation_probes"))
                        .as("rows visible through a shadow tenant_identity")
                        .isZero();
            }
        } finally {
            try (Connection superuser = db.superuser();
                    Statement statement = superuser.createStatement()) {
                statement.execute("REVOKE TEMP ON DATABASE \"" + db.database + "\" FROM \"" + db.runtimeRole + "\"");
            }
        }

        try (Connection owner = db.owner();
                Statement statement = owner.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT relrowsecurity FROM pg_class WHERE relname = 'isolation_probes' AND relkind = 'r'")) {
                rows.next();
                assertThat(rows.getBoolean(1)).as("RLS is still on").isTrue();
            }
            try (ResultSet rows =
                    statement.executeQuery("SELECT count(*) FROM pg_policies WHERE tablename = 'isolation_probes'")) {
                rows.next();
                assertThat(rows.getInt(1)).as("still one policy").isEqualTo(1);
            }
            assertThat(functionSource(owner))
                    .as("tenant_guard_ok() is unchanged")
                    .isEqualTo(functionBefore);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String functionSource(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery("SELECT prosrc FROM pg_proc WHERE proname = 'tenant_guard_ok'")) {
            return rows.next() ? rows.getString(1) : "(no such function)";
        }
    }

    /** The SQLState of a statement that must fail, or "no error" when it succeeds. */
    private static String sqlState(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return "no error";
        } catch (SQLException exception) {
            return exception.getSQLState();
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    /** "0" for no row, the SQLState for an error, "n" otherwise. */
    private static String stateOrCount(Connection connection, String sql) {
        try {
            return String.valueOf(count(connection, sql));
        } catch (SQLException exception) {
            return exception.getSQLState();
        }
    }

    private static String stateOrUpdateCount(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            return String.valueOf(statement.executeUpdate(sql));
        } catch (SQLException exception) {
            return exception.getSQLState();
        }
    }

    /** A malformed setting may read nothing or raise 22P02 (Q4); it may never return a row or change one. */
    private static String zeroOrInvalidText(String outcome) {
        return outcome.equals("0") || outcome.equals(INVALID_TEXT_REPRESENTATION) ? "ok" : outcome;
    }

    private static String oneOf(String actual, String... allowed) {
        for (String state : allowed) {
            if (state.equals(actual)) {
                return "ok";
            }
        }
        return actual;
    }
}
