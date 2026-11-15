package vn.tsolve.search.scope.repository;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
                WHERE m.user_id = :userId AND m.role_in_project = 'PROJECT_MANAGER'
                """, Map.of("userId", userId), (row, index) -> row.getLong("project_id")));
    }

    public Optional<String> writer(String fullName) {
        if (fullName.isBlank()) return Optional.empty();
        // Manual and Solution Form creation store the full name; imports retain their supplied author text.
        return jdbc
                .query(
                        "SELECT writer FROM ticket WHERE writer = :writer LIMIT 1",
                        Map.of("writer", fullName),
                        (row, index) -> row.getString("writer"))
                .stream()
                .findFirst();
    }
}
