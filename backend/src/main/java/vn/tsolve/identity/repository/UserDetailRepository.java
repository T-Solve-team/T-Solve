package vn.tsolve.identity.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.dto.SpaceRefResponse;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserHandoverOptionsResponse;
import vn.tsolve.identity.dto.UserProjectResponse;

@Repository
public class UserDetailRepository {
    private static final String PROJECTS =
            """
            SELECT p.public_id, p.jira_project_key, p.status,
                   d.public_id AS department_public_id, d.name AS department_name, pm.user_id AS manager_id
            FROM project_membership membership
            JOIN project p ON p.public_id = membership.project_id
            JOIN department d ON d.department_id = p.department_id
            JOIN project_membership pm ON pm.project_id = p.public_id AND pm.role_in_project = 'PROJECT_MANAGER'
            WHERE membership.user_id = :id
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public UserDetailRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccountResponse> account(UUID userId) {
        return accounts("u.id = :id", Map.of("id", userId)).stream().findFirst();
    }

    public List<UserProjectResponse> projects(UUID userId) {
        return jdbc.query(
                PROJECTS + " ORDER BY lower(p.jira_project_key), p.public_id",
                Map.of("id", userId),
                (rs, row) -> project(rs));
    }

    public List<SpaceRefResponse> managedSpaces(UUID userId) {
        return jdbc.query(
                """
                SELECT s.public_id, s.name FROM space s
                JOIN space_manager sm ON sm.space_id = s.space_id
                WHERE sm.user_id = :id ORDER BY lower(s.name), s.public_id
                """,
                Map.of("id", userId),
                (rs, row) -> new SpaceRefResponse(rs.getObject("public_id", UUID.class), rs.getString("name")));
    }

    public List<UUID> ticketResolverProjectIds(UUID userId) {
        return jdbc.query(
                """
                SELECT m.project_id FROM project_membership m
                JOIN project p ON p.public_id = m.project_id
                WHERE m.user_id = :id AND m.role_in_project = 'MEMBER'
                  AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = :id AND r.role = 'TICKET_RESOLVER')
                ORDER BY lower(p.jira_project_key), p.public_id
                """,
                Map.of("id", userId),
                (rs, row) -> rs.getObject("project_id", UUID.class));
    }

    public UserHandoverOptionsResponse handoverOptions(UUID userId) {
        List<UserProjectResponse> projects = jdbc.query(
                PROJECTS
                        + """
                 AND membership.role_in_project = 'PROJECT_MANAGER' AND p.status = 'ACTIVE'
                 ORDER BY lower(p.jira_project_key), p.public_id
                """,
                Map.of("id", userId),
                (rs, row) -> project(rs));
        List<UserAccountResponse> eligible = accounts("u.id <> :id AND u.status = 'ACTIVE'", Map.of("id", userId));
        List<UserAccountResponse> knowledgeManagers = eligible.stream()
                .filter(account -> account.roles().contains(Role.KNOWLEDGE_MANAGER))
                .toList();
        return new UserHandoverOptionsResponse(
                projects.stream()
                        .map(project -> new UserHandoverOptionsResponse.ProjectOption(
                                project,
                                eligible.stream()
                                        .filter(account -> account.roles().contains(Role.PROJECT_MANAGER)
                                                && account.department() != null
                                                && account.department()
                                                        .departmentId()
                                                        .equals(project.department()
                                                                .departmentId()))
                                        .toList()))
                        .toList(),
                managedSpaces(userId).stream()
                        .map(space -> new UserHandoverOptionsResponse.SpaceOption(space, knowledgeManagers))
                        .toList());
    }

    private List<UserAccountResponse> accounts(String predicate, Map<String, ?> parameters) {
        List<UserAccountResponse> rows = jdbc.query(
                """
                SELECT u.id, u.full_name, u.email, u.status, u.last_sign_in_at,
                       d.public_id AS department_public_id, d.name AS department_name
                FROM app_user u LEFT JOIN department d ON d.department_id = u.department_id
                WHERE
                """
                        + predicate + " ORDER BY lower(coalesce(u.full_name, u.email)), lower(u.email), u.id",
                parameters,
                (rs, row) -> account(rs));
        if (rows.isEmpty()) {
            return rows;
        }
        Map<UUID, EnumSet<Role>> roles = new HashMap<>();
        jdbc.query(
                "SELECT user_id, role FROM user_role WHERE user_id IN (:ids)",
                new MapSqlParameterSource(
                        "ids", rows.stream().map(UserAccountResponse::id).toList()),
                (RowCallbackHandler) rs -> roles.computeIfAbsent(
                                rs.getObject("user_id", UUID.class), id -> EnumSet.of(Role.EMPLOYEE))
                        .add(Role.valueOf(rs.getString("role"))));
        return rows.stream()
                .map(row -> new UserAccountResponse(
                        row.id(),
                        row.fullName(),
                        row.email(),
                        List.copyOf(roles.getOrDefault(row.id(), EnumSet.of(Role.EMPLOYEE))),
                        row.department(),
                        row.status(),
                        row.lastSignInAt()))
                .toList();
    }

    private static UserProjectResponse project(ResultSet rs) throws SQLException {
        return new UserProjectResponse(
                rs.getObject("public_id", UUID.class),
                rs.getString("jira_project_key"),
                new DepartmentRefResponse(
                        rs.getObject("department_public_id", UUID.class), rs.getString("department_name")),
                "ACTIVE".equals(rs.getString("status")),
                rs.getObject("manager_id", UUID.class));
    }

    private static UserAccountResponse account(ResultSet rs) throws SQLException {
        String name = rs.getString("full_name");
        UUID departmentId = rs.getObject("department_public_id", UUID.class);
        Timestamp signedIn = rs.getTimestamp("last_sign_in_at");
        return new UserAccountResponse(
                rs.getObject("id", UUID.class),
                name == null || name.isBlank() ? null : name,
                rs.getString("email"),
                List.of(Role.EMPLOYEE),
                departmentId == null ? null : new DepartmentRefResponse(departmentId, rs.getString("department_name")),
                AccountStatus.valueOf(rs.getString("status")),
                signedIn == null ? null : signedIn.toInstant());
    }
}
