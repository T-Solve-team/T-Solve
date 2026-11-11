package vn.tsolve.jiraapp.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only: the Department of a Project, for the Issue Panel's search rule (jira-app AC-008.6). The `project` module
 * has no such lookup yet and is not planned; move this into its service when it is (iteration-2 design, Task 5).
 */
@Repository
public class JiraProjectDepartmentRepository {

    private static final String DEPARTMENT =
            """
            SELECT d.public_id FROM project p JOIN department d ON d.department_id = p.department_id
            WHERE p.public_id = :projectId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JiraProjectDepartmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> departmentOf(UUID projectId) {
        return jdbc.queryForList(DEPARTMENT, Map.of("projectId", projectId), UUID.class).stream()
                .findFirst();
    }
}
