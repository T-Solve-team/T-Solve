package vn.tsolve.chatbot.issue.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IssueTargetRepository {
    private final JdbcTemplate jdbc;

    public IssueTargetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<IssueTarget> offeredDepartments() {
        return jdbc.query(
                """
                SELECT d.department_id, d.name, p.jira_project_key
                FROM department d JOIN project p ON p.project_id = d.default_project_id
                WHERE d.status = 'ACTIVE' AND p.status = 'ACTIVE' ORDER BY d.name
                """,
                (row, index) -> new IssueTarget(
                        row.getLong("department_id"), row.getString("name"), row.getString("jira_project_key")));
    }

    public Optional<String> linkedJiraAccountId(UUID accountId) {
        return jdbc.queryForList("""
                SELECT tracker_account_id FROM jira_account_links WHERE account_id = ?
                ORDER BY linked_at DESC LIMIT 1
                """, String.class, accountId).stream().findFirst();
    }

    public record IssueTarget(long departmentId, String name, String jiraProjectKey) {}
}
