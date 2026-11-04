package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.TwoTenantDatabases;

/** What only PostgreSQL can show for hand_off_code: the PDF column names and the check constraint of the target. */
class HandOffCodeSchemaIT {

    private static final String INSERT = "INSERT INTO hand_off_code"
            + " (target, workspace_id, account_id, operator_email, site_host, return_to, created_at, expires_at)"
            + " VALUES (?, ?, ?, ?, 'acme.localhost', '/', now(), now() + interval '60 seconds')";
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final String OPERATOR = "ops@example.test";

    private final JdbcTemplate db = TwoTenantDatabases.tenantA();

    @BeforeEach
    void clean() {
        db.update("DELETE FROM hand_off_code");
    }

    @Test
    @DisplayName("AC-001.16 the table has the Database Tables PDF columns, with a uuid key named id")
    void theTableHasThePdfColumns_AC_001_16() {
        List<String> columns = db.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'hand_off_code'", String.class);

        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "id",
                        "target",
                        "workspace_id",
                        "account_id",
                        "operator_email",
                        "site_host",
                        "return_to",
                        "created_at",
                        "expires_at",
                        "used_at");
        assertThat(db.queryForObject(
                        "SELECT data_type FROM information_schema.columns"
                                + " WHERE table_name = 'hand_off_code' AND column_name = 'id'",
                        String.class))
                .isEqualTo("uuid");
    }

    @Test
    @DisplayName("AC-001.16 a Workspace code and a Console code that fit the target are accepted")
    void validRowsAreAccepted_AC_001_16() {
        assertThatCode(() -> {
                    db.update(INSERT, "WORKSPACE", WORKSPACE, ACCOUNT, null);
                    db.update(INSERT, "PLATFORM_CONSOLE", null, null, OPERATOR);
                })
                .doesNotThrowAnyException();
        assertThat(db.queryForObject("SELECT count(*) FROM hand_off_code", Integer.class))
                .isEqualTo(2);
    }

    static List<Arguments> rowsThatBreakTheTargetRule() {
        return List.of(
                Arguments.of("WORKSPACE with an operator email", "WORKSPACE", WORKSPACE, ACCOUNT, OPERATOR),
                Arguments.of("WORKSPACE without a Workspace", "WORKSPACE", null, ACCOUNT, null),
                Arguments.of("WORKSPACE without an Account", "WORKSPACE", WORKSPACE, null, null),
                Arguments.of("PLATFORM_CONSOLE with a Workspace", "PLATFORM_CONSOLE", WORKSPACE, null, OPERATOR),
                Arguments.of("PLATFORM_CONSOLE with an Account", "PLATFORM_CONSOLE", null, ACCOUNT, OPERATOR),
                Arguments.of("PLATFORM_CONSOLE without an operator email", "PLATFORM_CONSOLE", null, null, null),
                Arguments.of("an unknown target", "OTHER", WORKSPACE, ACCOUNT, null));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("rowsThatBreakTheTargetRule")
    @DisplayName("AC-001.16 the check constraint on hand_off_code refuses a row that does not fit its target")
    void theCheckConstraintRefusesARowThatDoesNotFitItsTarget_AC_001_16(
            String label, String target, UUID workspace, UUID account, String operator) {
        assertThatThrownBy(() -> db.update(INSERT, target, workspace, account, operator))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_hand_off_code");
        assertThat(db.queryForObject("SELECT count(*) FROM hand_off_code", Integer.class))
                .isZero();
    }
}
