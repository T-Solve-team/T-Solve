package vn.tsolve.triage.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.triage.dto.ManagedProject;

/**
 * The Projects an account manages, read from {@code project_membership}: the same rule as ticket's import access
 * ({@code ProjectAccessRepository}) — an {@code ACTIVE} Project in an {@code ACTIVE} Department, managed by an
 * {@code ACTIVE} account that still holds the PROJECT_MANAGER Role.
 */
@Repository
public class ManagedProjectRepository {
    private static final String MANAGED = """
            SELECT p.project_id, p.public_id, p.jira_project_key
            FROM project p
            JOIN department d ON d.department_id = p.department_id AND d.status = 'ACTIVE'
            WHERE p.status = 'ACTIVE'
              AND EXISTS (
                  SELECT 1 FROM project_membership m
                  JOIN app_user u ON u.id = m.user_id AND u.status = 'ACTIVE'
                  JOIN user_role r ON r.user_id = u.id AND r.role = 'PROJECT_MANAGER'
                  WHERE m.project_id = p.public_id AND m.user_id = :accountId
                    AND m.role_in_project = 'PROJECT_MANAGER')
            ORDER BY p.jira_project_key
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ManagedProjectRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ManagedProject> managedBy(UUID accountId) {
        return jdbc.query(
                MANAGED,
                Map.of("accountId", accountId),
                (rs, row) -> new ManagedProject(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }
}
