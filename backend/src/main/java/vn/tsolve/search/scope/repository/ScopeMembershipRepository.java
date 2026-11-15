package vn.tsolve.search.scope.repository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Search-owned membership queries use the request's tenant DataSource. */
@Repository
public class ScopeMembershipRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ScopeMembershipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<Long> activeDepartments(UUID userId) {
        return new HashSet<>(jdbc.query("""
                SELECT d.department_id FROM app_user u
                JOIN department d ON d.department_id = u.department_id
                WHERE u.id = :userId AND d.status = 'ACTIVE'
                """, Map.of("userId", userId), (row, index) -> row.getLong("department_id")));
    }

    public Set<Long> managedProjects(UUID userId) {
        return new HashSet<>(jdbc.query("""
                SELECT p.project_id FROM project_membership m
                JOIN project p ON p.public_id = m.project_id
                WHERE m.user_id = :userId AND m.role_in_project = 'PROJECT_MANAGER' AND p.status = 'ACTIVE'
                """, Map.of("userId", userId), (row, index) -> row.getLong("project_id")));
    }
}
