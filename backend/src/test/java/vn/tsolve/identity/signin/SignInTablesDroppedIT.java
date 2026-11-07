package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.TwoTenantDatabases;

/**
 * Task 24 (K12, design risk row 120): the custom sign-in is gone, so the changelog drops its two tables. Only
 * "fresh migrate, then both tables are gone" is testable here; the files 004 to 007 are never edited.
 */
class SignInTablesDroppedIT {

    private final JdbcTemplate db = TwoTenantDatabases.tenantA();

    @Test
    @DisplayName("edge120 sign_in_states and hand_off_code are gone after the changelog runs")
    void signInTablesAreGoneAfterTheChangelogRuns_edge120() {
        assertThat(db.queryForList(
                        "SELECT table_name FROM information_schema.tables"
                                + " WHERE table_schema = current_schema()"
                                + " AND table_name IN ('sign_in_states', 'hand_off_code')",
                        String.class))
                .isEmpty();
    }
}
