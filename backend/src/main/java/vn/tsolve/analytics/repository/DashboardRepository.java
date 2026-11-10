package vn.tsolve.analytics.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ChatbotQuestion;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.DepartmentOption;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Feedback;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Gaps;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.NoResultSearch;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Options;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ProjectOption;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Suggestions;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.TicketFeedback;

@Repository
public class DashboardRepository {
    private static final String TICKET_SCOPE_JOINS =
            """
            join (
                select project_id, public_id as ticket_project_id from project
                union all
                select project_id, lpad(to_hex(project_id), 32, '0')::uuid from project
                where public_id <> lpad(to_hex(project_id), 32, '0')::uuid
            ) pk on pk.ticket_project_id = t.project_id
            join project p on p.project_id = pk.project_id
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

    /**
     * The values {@code ticket.project_id} may hold for the Projects of the scope: each Project's public id and its
     * legacy id. Resolved once per request so the ticket queries filter {@code t.project_id in (...)} on an index.
     */
    public List<UUID> findTicketProjectIds(Set<Long> departments, UUID projectId) {
        return jdbc
                .query(
                        "select public_id, lpad(to_hex(project_id), 32, '0')::uuid as legacy_id from project p where true"
                                + scopeFilter(departments, projectId),
                        scopeParameters(departments, projectId),
                        (row, index) ->
                                List.of(row.getObject("public_id", UUID.class), row.getObject("legacy_id", UUID.class)))
                .stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    /** {@code ticketProjects} must not be empty. */
    public List<OutcomeCount> findOutcomeCounts(List<UUID> ticketProjects, Instant start, Instant end) {
        var parameters = new MapSqlParameterSource("ticketProjects", ticketProjects)
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        String filter = " and t.project_id in (:ticketProjects)";
        String decisions =
                """
                select (r.decided_at at time zone 'Asia/Ho_Chi_Minh')::date as day,
                    p.project_id, p.public_id, p.jira_project_key, r.decision
                from review_decision r join ticket t on t.ticket_id = r.ticket_id
                """
                        + TICKET_SCOPE_JOINS
                        + " where r.decided_at >= :start and r.decided_at < :end"
                        + " and r.decision in ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED')"
                        + filter;
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

    /** {@code ticketProjects} must not be empty. */
    public List<TicketStatusCount> findTicketStatusCounts(List<UUID> ticketProjects) {
        return jdbc.query(
                """
                select d.public_id, d.name, t.status, count(*) as total
                from ticket t
                """
                        + TICKET_SCOPE_JOINS + " where t.project_id in (:ticketProjects)"
                        + """
                 group by d.department_id, d.public_id, d.name, t.status
                 order by lower(d.name), d.department_id, t.status
                """,
                Map.of("ticketProjects", ticketProjects),
                (row, index) -> new TicketStatusCount(
                        row.getObject("public_id", UUID.class),
                        row.getString("name"),
                        row.getString("status"),
                        row.getLong("total")));
    }

    /** {@code ticketProjects} must not be empty. */
    public Feedback findFeedback(List<UUID> ticketProjects, Instant start, Instant end) {
        var parameters = new MapSqlParameterSource("ticketProjects", ticketProjects)
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        var rows = jdbc.query(
                """
                with rated as (
                    select t.public_id, t.title,
                        count(*) filter (where f.rating = 'HELPFUL') as helpful,
                        count(*) filter (where f.rating = 'NOT_HELPFUL') as not_helpful,
                        count(*) as total,
                        (count(*) filter (where f.rating = 'HELPFUL'))::numeric / count(*) as rate
                    from ticket_feedback f join ticket t on t.ticket_id = f.ticket_id
                """
                        + TICKET_SCOPE_JOINS
                        + " where f.updated_at >= :start and f.updated_at < :end and t.project_id in (:ticketProjects)"
                        + """
                    group by t.ticket_id, t.public_id, t.title
                ), ranked as (
                    select *,
                        row_number() over (order by rate desc, total desc, title, public_id) as top_rank,
                        sum(helpful) over () as all_helpful,
                        sum(not_helpful) over () as all_not_helpful,
                        sum(helpful) over () / sum(total) over () as all_rate
                    from rated
                ), lowest as (
                    select public_id,
                        row_number() over (order by rate asc, total desc, title, public_id) as lowest_rank
                    from ranked where top_rank > 5
                )
                select ranked.*, lowest.lowest_rank from ranked left join lowest using (public_id)
                where top_rank <= 5 or lowest_rank <= 5 order by top_rank
                """,
                parameters,
                (row, index) -> new RankedFeedback(
                        new TicketFeedback(
                                row.getObject("public_id", UUID.class),
                                row.getString("title"),
                                row.getLong("helpful"),
                                row.getLong("not_helpful"),
                                row.getDouble("rate")),
                        row.getLong("top_rank"),
                        row.getLong("lowest_rank"),
                        row.getLong("all_helpful"),
                        row.getLong("all_not_helpful"),
                        row.getDouble("all_rate")));
        if (rows.isEmpty()) {
            return new Feedback(0, 0, null, List.of(), List.of());
        }
        var totals = rows.getFirst();
        var topRated = rows.stream()
                .filter(row -> row.topRank() <= 5)
                .map(RankedFeedback::ticket)
                .toList();
        var lowestRated = rows.stream()
                .filter(row -> row.topRank() > 5 && row.lowestRank() <= 5)
                .sorted(Comparator.comparingLong(RankedFeedback::lowestRank))
                .map(RankedFeedback::ticket)
                .toList();
        return new Feedback(totals.helpful(), totals.notHelpful(), totals.rate(), topRated, lowestRated);
    }

    /** Gaps follow the asker's Department, independently of the selected Project. */
    public Gaps findGaps(Set<Long> departments, Instant start, Instant end) {
        var parameters = new MapSqlParameterSource()
                .addValue("departments", departments)
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        var searches = jdbc.query(
                """
                select spelling, total from (
                    select distinct on (lower(trim(query)))
                        lower(trim(query)) as grouping_key, trim(query) as spelling,
                        count(*) over (partition by lower(trim(query))) as total
                    from search_log
                    where result_count = 0 and created_at >= :start and created_at < :end
                """
                        + (departments == null ? "" : " and department_id in (:departments)")
                        + """
                    order by lower(trim(query)), created_at, search_log_id
                ) grouped order by total desc, grouping_key limit 10
                """,
                parameters,
                (row, index) -> new NoResultSearch(row.getString("spelling"), row.getLong("total")));
        var questions = jdbc.query(
                """
                with grouped as (
                    select distinct on (lower(trim(a.question)))
                        lower(trim(a.question)) as grouping_key, trim(a.question) as spelling,
                        count(*) over (partition by lower(trim(a.question))) as total
                    from chatbot_answer a join app_user u on u.id = a.user_id
                    where a.outcome = 'NO_SOURCE' and a.created_at >= :start and a.created_at < :end
                """
                        + (departments == null ? "" : " and u.department_id in (:departments)")
                        + """
                    order by lower(trim(a.question)), a.created_at, a.chatbot_answer_id
                )
                select spelling, total, sum(total) over () as no_source
                from grouped order by total desc, grouping_key limit 10
                """,
                parameters,
                (row, index) -> new QuestionCount(
                        new ChatbotQuestion(row.getString("spelling"), row.getLong("total")),
                        row.getLong("no_source")));
        return new Gaps(
                searches,
                questions.isEmpty() ? 0 : questions.getFirst().noSource(),
                questions.stream().map(QuestionCount::question).toList());
    }

    /** {@code ticketProjects} must not be empty; {@code suggestion_result.project_id} holds the public id. */
    public Suggestions findSuggestions(List<UUID> ticketProjects, Instant start, Instant end) {
        return jdbc.queryForObject(
                """
                select count(*) as posted,
                    count(*) filter (where s.reporter_answer = 'YES') as solved,
                    (count(*) filter (where s.reporter_answer = 'YES'))::numeric / nullif(count(*), 0) as solved_share
                from suggestion_result s
                where s.commented_at >= :start and s.commented_at < :end and s.project_id in (:ticketProjects)
                """,
                new MapSqlParameterSource("ticketProjects", ticketProjects)
                        .addValue("start", Timestamp.from(start))
                        .addValue("end", Timestamp.from(end)),
                (row, index) -> {
                    var solvedShare = row.getBigDecimal("solved_share");
                    return new Suggestions(
                            row.getLong("posted"),
                            row.getLong("solved"),
                            solvedShare == null ? null : solvedShare.doubleValue());
                });
    }

    private record RankedFeedback(
            TicketFeedback ticket, long topRank, long lowestRank, long helpful, long notHelpful, double rate) {}

    private record QuestionCount(ChatbotQuestion question, long noSource) {}

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
