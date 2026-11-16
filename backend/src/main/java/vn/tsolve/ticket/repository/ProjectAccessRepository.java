package vn.tsolve.ticket.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Who may import into a Project, read from {@code project_membership} of the bound Workspace (BUG-300). The same
 * rule as identity's scope check: a {@code PROJECT_MANAGER} row counts only while the account holds that Role.
 */
@Repository
public class ProjectAccessRepository {
    private static final String ACTIVE_PROJECT = """
            project p JOIN department d ON d.department_id = p.department_id AND d.status = 'ACTIVE'
            WHERE p.public_id = :projectId AND p.status = 'ACTIVE'
            """;

    private static final String MANAGES_ACTIVE = "SELECT EXISTS (SELECT 1 FROM " + ACTIVE_PROJECT + """
              AND EXISTS (
                  SELECT 1 FROM project_membership m
                  JOIN app_user u ON u.id = m.user_id AND u.status = 'ACTIVE'
                  JOIN user_role r ON r.user_id = u.id AND r.role = 'PROJECT_MANAGER'
                  WHERE m.project_id = p.public_id AND m.user_id = :accountId
                    AND m.role_in_project = 'PROJECT_MANAGER'))
            """;

    private static final String IS_ACTIVE = "SELECT EXISTS (SELECT 1 FROM " + ACTIVE_PROJECT + ")";

    private final NamedParameterJdbcTemplate jdbc;

    public ProjectAccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean managesActive(UUID accountId, UUID projectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                MANAGES_ACTIVE, Map.of("accountId", accountId, "projectId", projectId), Boolean.class));
    }

    public boolean isActive(UUID projectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(IS_ACTIVE, Map.of("projectId", projectId), Boolean.class));
    }

    public Optional<String> jiraProjectKey(UUID projectId) {
        return jdbc
                .query(
                        "SELECT jira_project_key FROM project WHERE public_id = :projectId",
                        Map.of("projectId", projectId),
                        (rs, row) -> rs.getString(1))
                .stream()
                .findFirst();
    }
}
