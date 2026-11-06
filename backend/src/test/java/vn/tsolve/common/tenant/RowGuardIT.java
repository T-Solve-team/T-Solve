package vn.tsolve.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RowGuardIT {
    private static TenantRowGuardDatabase tenantA;
    private static TenantRowGuardDatabase tenantB;

    @BeforeAll
    static void databases() throws Exception {
        tenantA = TenantRowGuardDatabase.create();
        tenantB = TenantRowGuardDatabase.create();
    }

    @BeforeEach
    void seed() {
        tenantA.seed();
        tenantB.seed();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong-workspace", "00000000-0000-0000-0000-000000000000"})
    void hidesRowsAndRefusesInsertWithoutMatchingSetting_AC_001_30(String setting) throws Exception {
        try (var connection = tenantA.runtime(setting)) {
            assertThat(rows(connection)).isZero();
            assertThatThrownBy(() -> connection
                            .createStatement()
                            .executeUpdate("INSERT INTO isolation_probes (payload) VALUES ('blocked')"))
                    .isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState())
                    .isEqualTo("42501");
        }
        assertThat(tenantA.owner.queryForObject("SELECT count(*) FROM isolation_probes", Long.class))
                .isEqualTo(1);
    }

    @Test
    void allowsReadInsertUpdateDeleteOnlyForTheMatchingWorkspace_AC_001_30() throws Exception {
        try (var connection = tenantA.runtime(tenantA.workspaceId.toString())) {
            assertThat(rows(connection)).isEqualTo(1);
            try (var statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("INSERT INTO isolation_probes (payload) VALUES ('own')"))
                        .isEqualTo(1);
                assertThat(statement.executeUpdate("UPDATE isolation_probes SET payload='updated' WHERE payload='own'"))
                        .isEqualTo(1);
                assertThat(statement.executeUpdate("DELETE FROM isolation_probes WHERE payload='updated'"))
                        .isEqualTo(1);
            }
            connection.commit();
        }
    }

    @Test
    void misroutingToAnotherDatabaseCannotReadOrChangeItsRows_AC_001_30() throws Exception {
        try (var connection = tenantB.runtime(tenantA.workspaceId.toString());
                var statement = connection.createStatement()) {
            assertThat(rows(connection)).isZero();
            assertThat(statement.executeUpdate("UPDATE isolation_probes SET payload='leak'"))
                    .isZero();
            assertThat(statement.executeUpdate("DELETE FROM isolation_probes")).isZero();
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO isolation_probes (payload) VALUES ('leak')"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(tenantB.owner.queryForObject("SELECT payload FROM isolation_probes", String.class))
                .isEqualTo(tenantB.databaseName);
    }

    @Test
    void transactionLocalSettingDoesNotSurviveConnectionReuse_AC_001_30() throws Exception {
        try (var connection = tenantA.runtime(tenantA.workspaceId.toString())) {
            assertThat(rows(connection)).isEqualTo(1);
            connection.commit();
            assertThat(rows(connection)).isZero();
            connection.rollback();
        }
    }

    @Test
    void emptyIdentityFailsClosed_AC_001_28_AC_001_30() throws Exception {
        tenantA.owner.update("DELETE FROM tenant_identity");
        try (var connection = tenantA.runtime(tenantA.workspaceId.toString())) {
            assertThat(rows(connection)).isZero();
            assertThatThrownBy(() -> connection
                            .createStatement()
                            .executeUpdate("INSERT INTO isolation_probes (payload) VALUES ('blocked')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void ownerCannotInsertASecondIdentity_AC_001_28() {
        assertThatThrownBy(() ->
                        tenantA.owner.update("INSERT INTO tenant_identity (tenant_id) VALUES (?)", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tenantA.owner.update(
                        "INSERT INTO tenant_identity (singleton, tenant_id) VALUES (false, ?)", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(tenantA.owner.queryForObject("SELECT count(*) FROM tenant_identity", Long.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DELETE FROM tenant_identity",
                "UPDATE tenant_identity SET tenant_id=gen_random_uuid()",
                "INSERT INTO tenant_identity (tenant_id) VALUES (gen_random_uuid())",
                "TRUNCATE tenant_identity",
                "TRUNCATE isolation_probes",
                "ALTER TABLE isolation_probes DISABLE ROW LEVEL SECURITY",
                "DROP POLICY tenant_guard ON isolation_probes"
            })
    void runtimeCannotChangeIdentityOrBypassPolicies_AC_001_28_AC_001_32(String sql) throws Exception {
        try (var connection = tenantA.runtime(tenantA.workspaceId.toString())) {
            assertThatThrownBy(() -> connection.createStatement().execute(sql))
                    .isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState())
                    .isEqualTo("42501");
        }
    }

    @Test
    void ownerCanBackfillWithoutATenantSetting_AC_001_32() {
        assertThat(tenantA.owner.queryForObject("SELECT count(*) FROM isolation_probes", Long.class))
                .isEqualTo(1);
        assertThat(tenantA.owner.update("UPDATE isolation_probes SET payload='owner-backfill'"))
                .isEqualTo(1);
    }

    private static long rows(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM isolation_probes")) {
            result.next();
            return result.getLong(1);
        }
    }
}
