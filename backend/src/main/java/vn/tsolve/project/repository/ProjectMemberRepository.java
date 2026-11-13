package vn.tsolve.project.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.project.dto.MemberCandidateResponse;
import vn.tsolve.project.dto.ProjectMemberResponse;
import vn.tsolve.project.dto.ProjectSummaryResponse;
import vn.tsolve.project.entity.ProjectStatus;

@Repository
public class ProjectMemberRepository {
    public static final int CANDIDATE_LIMIT = 20;

    // An Active account holding the Ticket Resolver role that is not yet a member of the Project.
    private static final String ELIGIBLE = """
            u.status = 'ACTIVE'
            AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'TICKET_RESOLVER')
            AND NOT EXISTS (SELECT 1 FROM project_membership m WHERE m.user_id = u.id AND m.project_id = :project)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProjectMemberRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProjectSummaryResponse> findProject(UUID projectId) {
        return jdbc
                .query(
                        """
                        SELECT p.public_id, p.jira_project_key, d.name AS department_name, u.full_name, p.status
                        FROM project p
                        JOIN department d ON d.department_id = p.department_id
                        LEFT JOIN project_membership m
                            ON m.project_id = p.public_id AND m.role_in_project = 'PROJECT_MANAGER'
                        LEFT JOIN app_user u ON u.id = m.user_id
                        WHERE p.public_id = :project
                        """,
                        Map.of("project", projectId),
                        (rs, row) -> new ProjectSummaryResponse(
                                rs.getObject("public_id", UUID.class),
                                rs.getString("jira_project_key"),
                                rs.getString("department_name"),
                                rs.getString("full_name"),
                                ProjectStatus.valueOf(rs.getString("status"))))
                .stream()
                .findFirst();
    }

    /** Design D3: an Active account with the Project Manager role and the Project's PROJECT_MANAGER row. */
    public boolean isManager(UUID accountId, UUID projectId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM app_user u
                    JOIN project_membership m ON m.user_id = u.id
                    WHERE u.id = :account AND u.status = 'ACTIVE'
                      AND m.project_id = :project AND m.role_in_project = 'PROJECT_MANAGER'
                      AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROJECT_MANAGER'))
                """, Map.of("account", accountId, "project", projectId), Boolean.class));
    }

    public List<ProjectMemberResponse> members(UUID projectId) {
        return jdbc.query(
                """
                SELECT u.id, u.full_name, u.email, m.role_in_project, m.date_added
                FROM project_membership m JOIN app_user u ON u.id = m.user_id
                WHERE m.project_id = :project
                ORDER BY m.role_in_project <> 'PROJECT_MANAGER', lower(u.full_name) NULLS LAST, u.email
                """,
                Map.of("project", projectId),
                (rs, row) -> new ProjectMemberResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        ProjectMemberResponse.Role.valueOf(rs.getString("role_in_project")),
                        rs.getObject("date_added", OffsetDateTime.class).toInstant()));
    }

    /** {@code q} is trimmed; it is matched literally, ignoring case, in the full name or email. */
    public List<MemberCandidateResponse> candidates(UUID projectId, String q) {
        String pattern = "%"
                + q.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";
        return jdbc.query(
                "SELECT u.id, u.full_name, u.email FROM app_user u WHERE " + ELIGIBLE + """
                        AND (lower(coalesce(u.full_name, '')) LIKE :pattern ESCAPE '\\'
                             OR lower(u.email) LIKE :pattern ESCAPE '\\')
                        ORDER BY lower(u.full_name) NULLS LAST, u.email
                        LIMIT :limit
                        """,
                Map.of("project", projectId, "pattern", pattern, "limit", CANDIDATE_LIMIT),
                (rs, row) -> new MemberCandidateResponse(
                        rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email")));
    }

    /**
     * How many of {@code userIds} may be added. Locks those accounts FOR SHARE so identity's role removal
     * ({@code FOR UPDATE}) cannot interleave with the add.
     */
    public int lockEligible(UUID projectId, List<UUID> userIds) {
        return jdbc.query(
                        "SELECT u.id FROM app_user u WHERE u.id IN (:ids) AND " + ELIGIBLE + " FOR SHARE OF u",
                        Map.of("project", projectId, "ids", userIds),
                        (rs, row) -> rs.getObject(1, UUID.class))
                .size();
    }

    public void addMembers(UUID projectId, List<UUID> userIds) {
        jdbc.batchUpdate(
                """
                INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (:user, :project, 'MEMBER')
                """,
                userIds.stream()
                        .map(user -> new MapSqlParameterSource("user", user).addValue("project", projectId))
                        .toArray(MapSqlParameterSource[]::new));
    }
}
