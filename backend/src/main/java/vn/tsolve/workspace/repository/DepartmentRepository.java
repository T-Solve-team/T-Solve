package vn.tsolve.workspace.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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

    /** The default Project reads as none unless it is Active (AC-012.3). */
    public record Current(long id, String name, UUID defaultProjectId, String defaultProjectKey) {}

    public record ProjectOption(long rowId, UUID projectId, String jiraProjectKey) {}

    private static final String CURRENT = """
            SELECT d.department_id, d.name, p.public_id, p.jira_project_key
            FROM department d
            LEFT JOIN project p ON p.project_id = d.default_project_id AND p.status = 'ACTIVE'
            WHERE d.public_id = :id AND d.status = 'ACTIVE'
            """;

    private static final String PROJECT = """
            SELECT project_id, public_id, jira_project_key FROM project
            WHERE public_id = :projectId AND department_id = :departmentId AND status = 'ACTIVE'
            """;

    public Optional<Current> findActive(UUID publicId) {
        return current(CURRENT, publicId);
    }

    // FOR UPDATE: the second of two saves waits here, then reads the first's result (AC-003.16, AC-012.16).
    // Lock and read are two statements: after the wait, a locking join re-checks only the locked row and keeps
    // the joined Project it saw before, so the second save would audit a stale default (BUG-314).
    public Optional<Current> lockActive(UUID publicId) {
        List<Long> locked = jdbc.queryForList(
                "SELECT department_id FROM department WHERE public_id = :id AND status = 'ACTIVE' FOR UPDATE",
                Map.of("id", publicId),
                Long.class);
        return locked.isEmpty() ? Optional.empty() : findActive(publicId);
    }

    private Optional<Current> current(String sql, UUID publicId) {
        return jdbc
                .query(
                        sql,
                        Map.of("id", publicId),
                        (rs, row) -> new Current(
                                rs.getLong(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4)))
                .stream()
                .findFirst();
    }

    /** The Department's Active Projects ordered by key (AC-012.1). */
    public List<ProjectOption> activeProjects(long departmentId) {
        return jdbc.query("""
                SELECT project_id, public_id, jira_project_key FROM project
                WHERE department_id = :departmentId AND status = 'ACTIVE' ORDER BY jira_project_key
                """, Map.of("departmentId", departmentId), (rs, row) -> projectOption(rs));
    }

    public Optional<ProjectOption> findActiveProject(long departmentId, UUID projectId) {
        return project(PROJECT, departmentId, projectId);
    }

    // FOR SHARE: the Project cannot be Removed between this read and the commit (AC-012.13).
    public Optional<ProjectOption> lockActiveProject(long departmentId, UUID projectId) {
        return project(PROJECT + "FOR SHARE", departmentId, projectId);
    }

    private Optional<ProjectOption> project(String sql, long departmentId, UUID projectId) {
        return jdbc
                .query(
                        sql,
                        Map.of("departmentId", departmentId, "projectId", projectId),
                        (rs, row) -> projectOption(rs))
                .stream()
                .findFirst();
    }

    private static ProjectOption projectOption(ResultSet rs) throws SQLException {
        return new ProjectOption(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3));
    }

    /** {@code projectRowId} null clears the default Project. */
    public void setDefaultProject(long id, Long projectRowId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("id", id).addValue("projectRowId", projectRowId, Types.BIGINT);
        jdbc.update("UPDATE department SET default_project_id = :projectRowId WHERE department_id = :id", params);
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
