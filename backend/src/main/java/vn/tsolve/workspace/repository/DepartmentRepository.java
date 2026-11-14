package vn.tsolve.workspace.repository;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.common.exception.BusinessRuleException;

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

    public record Current(long id, String name) {}

    public Optional<Current> findActive(UUID publicId) {
        return current(
                "SELECT department_id, name FROM department WHERE public_id = :id AND status = 'ACTIVE'", publicId);
    }

    // FOR UPDATE: the second of two saves waits here and reads the first's name (AC-003.16).
    public Optional<Current> lockActive(UUID publicId) {
        return current(
                "SELECT department_id, name FROM department WHERE public_id = :id AND status = 'ACTIVE' FOR UPDATE",
                publicId);
    }

    private Optional<Current> current(String sql, UUID publicId) {
        return jdbc
                .query(sql, Map.of("id", publicId), (rs, row) -> new Current(rs.getLong(1), rs.getString(2)))
                .stream()
                .findFirst();
    }

    // Only uq_department_name and uq_department_name_lower can fail on SET name: 23505 means the name is taken.
    public void renameDepartment(long id, String name) {
        try {
            jdbc.update("UPDATE department SET name = :name WHERE department_id = :id", Map.of("id", id, "name", name));
        } catch (DataIntegrityViolationException failure) {
            if (failure.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                throw new BusinessRuleException("MSG43", Map.of(), "name");
            }
            throw failure;
        }
    }

    // space.department_id is UNIQUE, so 0 or 1 row; none is broken data and rolls the rename back.
    public UUID renameSpace(long departmentId, String name) {
        return jdbc
                .query(
                        "UPDATE space SET name = :name WHERE department_id = :id RETURNING public_id",
                        Map.of("id", departmentId, "name", name),
                        (rs, row) -> rs.getObject(1, UUID.class))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Department " + departmentId + " has no Space"));
    }
}
