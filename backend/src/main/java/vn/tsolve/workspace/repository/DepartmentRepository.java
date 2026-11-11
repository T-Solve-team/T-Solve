package vn.tsolve.workspace.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DepartmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Created(long id, UUID publicId) {}

    // No target: uq_department_name, uq_department_name_lower, uq_department_public_id arbitrate; conflict: name taken.
    public Optional<Created> insertDepartment(String name) {
        return jdbc
                .query(
                        """
                INSERT INTO department (name) VALUES (:name)
                ON CONFLICT DO NOTHING
                RETURNING department_id, public_id
                """,
                        Map.of("name", name),
                        (rs, row) -> new Created(rs.getLong("department_id"), rs.getObject("public_id", UUID.class)))
                .stream()
                .findFirst();
    }

    public UUID insertSpace(long departmentId, String name) {
        return jdbc.queryForObject("""
                INSERT INTO space (department_id, name) VALUES (:departmentId, :name)
                RETURNING public_id
                """, Map.of("departmentId", departmentId, "name", name), UUID.class);
    }
}
