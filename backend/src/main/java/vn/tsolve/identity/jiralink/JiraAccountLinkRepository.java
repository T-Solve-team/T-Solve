package vn.tsolve.identity.jiralink;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes {@code jira_account_links} in the company database bound to the current request (AC-001.71). The table has
 * no Workspace column: the database is the Workspace.
 */
@Repository
public class JiraAccountLinkRepository {

    private final JdbcTemplate jdbc;

    public JiraAccountLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Links the tracker account to the Account in one statement; an earlier link of the same tracker account goes. */
    public void upsert(JiraAccountLink link) {
        jdbc.update(
                "INSERT INTO jira_account_links (tracker_account_id, account_id, linked_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (tracker_account_id) DO UPDATE "
                        + "SET account_id = EXCLUDED.account_id, linked_at = EXCLUDED.linked_at",
                link.trackerAccountId(),
                link.accountId(),
                Timestamp.from(link.linkedAt()));
    }
}
