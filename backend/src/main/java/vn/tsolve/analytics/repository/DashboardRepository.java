package vn.tsolve.analytics.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DepartmentOption;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Options;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ProjectOption;

@Repository
public class DashboardRepository {
    private static final String TICKET_SCOPE_JOINS =
            """
            join project p on (p.public_id = t.project_id
                or lpad(to_hex(p.project_id), 32, '0')::uuid = t.project_id)
            join department d on d.department_id = p.department_id
            """;
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

    public List<OutcomeCount> findOutcomeCounts(Set<Long> departments, UUID projectId, Instant start, Instant end) {
        var parameters = scopeParameters(departments, projectId)
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        String filter = scopeFilter(departments, projectId);
        String decisions =
                """
                select (r.decided_at at time zone 'Asia/Ho_Chi_Minh')::date as day,
                    p.project_id, p.public_id, p.jira_project_key, r.decision
                from review_decision r join ticket t on t.ticket_id = r.ticket_id
                """
                        + TICKET_SCOPE_JOINS + " where r.decided_at >= :start and r.decided_at < :end" + filter;
        String imports =
                """
                select (t.created_at at time zone 'Asia/Ho_Chi_Minh')::date as day,
                    p.project_id, p.public_id, p.jira_project_key, 'APPROVED' as decision
                from ticket t
                """
                        + TICKET_SCOPE_JOINS
                        + " where t.source <> 'SOLUTION_FORM' and t.created_at >= :start and t.created_at < :end"
                        + filter;
        return jdbc.query(
                """
                select day, public_id, jira_project_key,
                    count(*) filter (where decision = 'APPROVED') as approved,
                    count(*) filter (where decision = 'REJECTED') as rejected,
                    count(*) filter (where decision = 'CHANGES_REQUESTED') as changes_requested
                from (
                """
                        + decisions + " union all " + imports
                        + """
                ) events
                group by day, project_id, public_id, jira_project_key
                order by lower(jira_project_key), project_id, day
                """,
                parameters,
                (row, index) -> new OutcomeCount(
                        row.getObject("day", LocalDate.class),
                        row.getObject("public_id", UUID.class),
                        row.getString("jira_project_key"),
                        row.getLong("approved"),
                        row.getLong("rejected"),
                        row.getLong("changes_requested")));
    }

    public List<TicketStatusCount> findTicketStatusCounts(Set<Long> departments, UUID projectId) {
        return jdbc.query(
                """
                select d.public_id, d.name, t.status, count(*) as total
                from ticket t
                """
                        + TICKET_SCOPE_JOINS + " where true" + scopeFilter(departments, projectId)
                        + """
                 group by d.department_id, d.public_id, d.name, t.status
                 order by lower(d.name), d.department_id, t.status
                """,
                scopeParameters(departments, projectId),
                (row, index) -> new TicketStatusCount(
                        row.getObject("public_id", UUID.class),
                        row.getString("name"),
                        row.getString("status"),
                        row.getLong("total")));
    }

    private String scopeFilter(Set<Long> departments, UUID projectId) {
        return (departments == null ? "" : " and p.department_id in (:departments)")
                + (projectId == null ? "" : " and p.public_id = :projectId");
    }

    private MapSqlParameterSource scopeParameters(Set<Long> departments, UUID projectId) {
        return new MapSqlParameterSource().addValue("departments", departments).addValue("projectId", projectId);
    }

    public record OutcomeCount(
            LocalDate date, UUID projectId, String name, long approved, long rejected, long changesRequested) {}

    public record TicketStatusCount(UUID departmentId, String name, String status, long count) {}

    public record Department(long id, UUID publicId, String name) {}

    public record Project(UUID publicId, long departmentId) {}
}
