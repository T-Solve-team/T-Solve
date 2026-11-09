package vn.tsolve.analytics.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DepartmentOption;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Options;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ProjectOption;

@Repository
public class DashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findUserDepartment(UUID userId) {
        return jdbc
                .query(
                        "select department_id from app_user where id = :id and department_id is not null",
                        Map.of("id", userId),
                        (row, index) -> row.getLong("department_id"))
                .stream()
                .findFirst();
    }

    public Optional<Department> findDepartment(UUID publicId) {
        return jdbc
                .query(
                        "select department_id, public_id, name from department where public_id = :id",
                        Map.of("id", publicId),
                        (row, index) -> new Department(
                                row.getLong("department_id"),
                                row.getObject("public_id", UUID.class),
                                row.getString("name")))
                .stream()
                .findFirst();
    }

    public Optional<Project> findProject(UUID publicId) {
        return jdbc
                .query(
                        "select public_id, department_id from project where public_id = :id",
                        Map.of("id", publicId),
                        (row, index) ->
                                new Project(row.getObject("public_id", UUID.class), row.getLong("department_id")))
                .stream()
                .findFirst();
    }

    /** Null selects Admin options; a department pk selects all PM options regardless of status. */
    public Options findOptions(Long ownDepartment) {
        Map<String, ?> parameters = ownDepartment == null ? Map.of() : Map.of("department", ownDepartment);
        String departmentFilter = ownDepartment == null ? "d.status = 'ACTIVE'" : "d.department_id = :department";
        String projectFilter =
                ownDepartment == null ? "p.status = 'ACTIVE' and d.status = 'ACTIVE'" : "d.department_id = :department";
        var departments = jdbc.query(
                "select d.public_id, d.name from department d where " + departmentFilter
                        + " order by lower(d.name), d.department_id",
                parameters,
                (row, index) -> new DepartmentOption(row.getObject("public_id", UUID.class), row.getString("name")));
        var projects = jdbc.query(
                """
                select p.public_id, p.jira_project_key, d.public_id as department_public_id
                from project p join department d on d.department_id = p.department_id
                where
                """
                        + projectFilter + " order by lower(p.jira_project_key), p.project_id",
                parameters,
                (row, index) -> new ProjectOption(
                        row.getObject("public_id", UUID.class),
                        row.getString("jira_project_key"),
                        row.getObject("department_public_id", UUID.class)));
        return new Options(departments, projects);
    }

    public record Department(long id, UUID publicId, String name) {}

    public record Project(UUID publicId, long departmentId) {}
}
