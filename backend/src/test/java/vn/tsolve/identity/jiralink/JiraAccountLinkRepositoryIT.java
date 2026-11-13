package vn.tsolve.identity.jiralink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.SignInTestFixtures;

/**
 * The shape of {@code jira_account_links} in a company database (AC-001.71). The writes and the replace rule are
 * driven end to end in {@code JiraLinkControllerTest}; this class guards what only the database can enforce.
 */
@SpringBootTest
@Import(TwoTenantDatabases.class)
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class JiraAccountLinkRepositoryIT {

    @BeforeEach
    void clean() {
        TwoTenantDatabases.tenantA().update("TRUNCATE jira_account_links");
        TwoTenantDatabases.tenantA().update("TRUNCATE app_user CASCADE");
    }

    @Test
    @DisplayName("AC-001.71 edge 1 jira_account_links has no workspace_id column and tracker_account_id is unique")
    void hasNoWorkspaceIdAndTrackerAccountIdIsUnique() {
        JdbcTemplate db = TwoTenantDatabases.tenantA();

        List<String> columns = db.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = 'jira_account_links'",
                String.class);

        assertThat(columns)
                .as("the table exists with its columns")
                .contains("id", "tracker_account_id", "account_id", "linked_at");
        assertThat(columns).as("one database per Workspace: no workspace_id").doesNotContain("workspace_id");

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        for (UUID account : List.of(first, second)) {
            db.update(
                    "INSERT INTO app_user (id, email, full_name, status, created_at) VALUES (?, ?, 'X', 'ACTIVE', now())",
                    account,
                    account + "@acme.example");
        }
        String insert =
                "INSERT INTO jira_account_links (tracker_account_id, account_id, linked_at) " + "VALUES (?, ?, now())";
        db.update(insert, "jira-acct-1", first);

        assertThatThrownBy(() -> db.update(insert, "jira-acct-1", second))
                .as("the database refuses a second row for one tracker account")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM jira_account_links", Integer.class))
                .isEqualTo(1);
    }
}
