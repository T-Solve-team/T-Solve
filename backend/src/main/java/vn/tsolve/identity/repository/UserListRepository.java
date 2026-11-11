package vn.tsolve.identity.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.AccountStatus;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.identity.dto.UserAccountResponse;
import vn.tsolve.identity.dto.UserListQuery;

@Repository
public class UserListRepository {
    private static final String FROM = " FROM app_user u LEFT JOIN department d ON d.department_id = u.department_id ";
    private final NamedParameterJdbcTemplate jdbc;

    public UserListRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<UserAccountResponse> list(UserListQuery query) {
        var parameters = new MapSqlParameterSource();
        var clauses = new ArrayList<String>();
        if (!query.q().isEmpty()) {
            clauses.add(
                    "(lower(coalesce(u.full_name,'')) LIKE :pattern ESCAPE '\\' OR lower(u.email) LIKE :pattern ESCAPE '\\')");
            parameters.addValue("pattern", query.pattern());
        }
        if (query.role() == Role.EMPLOYEE) {
            clauses.add("NOT EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id)");
        } else if (query.role() != null) {
            clauses.add("EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = :role)");
            parameters.addValue("role", query.role().name());
        }
        if (query.status() != null) {
            clauses.add("u.status = :status");
            parameters.addValue("status", query.status().name());
        }
        if (query.departmentId() != null) {
            clauses.add("d.public_id = :departmentId");
            parameters.addValue("departmentId", query.departmentId());
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        long count = jdbc.queryForObject("SELECT count(*)" + FROM + where, parameters, Long.class);
        String column =
                switch (query.sortField()) {
                    case "email" -> "lower(u.email)";
                    case "lastSignInAt" -> "u.last_sign_in_at";
                    default -> "lower(coalesce(u.full_name,''))";
                };
        String direction = query.descending() ? " DESC NULLS LAST" : " ASC NULLS FIRST";
        parameters.addValue("limit", query.size()).addValue("offset", (long) query.page() * query.size());
        List<UserAccountResponse> rows = jdbc.query(
                """
                SELECT u.id, u.full_name, u.email, u.status, u.last_sign_in_at,
                       d.public_id AS department_public_id, d.name AS department_name
                """
                        + FROM + where + " ORDER BY " + column + direction + ", u.id ASC LIMIT :limit OFFSET :offset",
                parameters,
                (rs, row) -> account(rs));
        Map<UUID, EnumSet<Role>> roles = new HashMap<>();
        if (!rows.isEmpty()) {
            jdbc.query(
                    "SELECT user_id, role FROM user_role WHERE user_id IN (:ids)",
                    new MapSqlParameterSource(
                            "ids", rows.stream().map(UserAccountResponse::id).toList()),
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> roles.computeIfAbsent(
                                    rs.getObject("user_id", UUID.class), id -> EnumSet.of(Role.EMPLOYEE))
                            .add(Role.valueOf(rs.getString("role"))));
        }
        List<UserAccountResponse> content = rows.stream()
                .map(row -> new UserAccountResponse(
                        row.id(),
                        row.fullName(),
                        row.email(),
                        List.copyOf(roles.getOrDefault(row.id(), EnumSet.of(Role.EMPLOYEE))),
                        row.department(),
                        row.status(),
                        row.lastSignInAt()))
                .toList();
        return new PageImpl<>(content, PageRequest.of(query.page(), query.size()), count);
    }

    public List<DepartmentRefResponse> departments() {
        return jdbc.query(
                "SELECT public_id, name FROM department WHERE status = 'ACTIVE' ORDER BY lower(name), department_id",
                Map.of(),
                (rs, row) -> new DepartmentRefResponse(rs.getObject("public_id", UUID.class), rs.getString("name")));
    }

    public UserAccountResponse findById(UUID id) {
        UserAccountResponse row = jdbc.queryForObject(
                """
                SELECT u.id, u.full_name, u.email, u.status, u.last_sign_in_at,
                       d.public_id AS department_public_id, d.name AS department_name
                """
                        + FROM + " WHERE u.id = :id",
                Map.of("id", id),
                (rs, index) -> account(rs));
        EnumSet<Role> roles = EnumSet.of(Role.EMPLOYEE);
        roles.addAll(jdbc.query(
                "SELECT role FROM user_role WHERE user_id = :id",
                Map.of("id", id),
                (rs, index) -> Role.valueOf(rs.getString("role"))));
        return new UserAccountResponse(
                row.id(),
                row.fullName(),
                row.email(),
                List.copyOf(roles),
                row.department(),
                row.status(),
                row.lastSignInAt());
    }

    private UserAccountResponse account(ResultSet rs) throws SQLException {
        String name = rs.getString("full_name");
        UUID departmentId = rs.getObject("department_public_id", UUID.class);
        Timestamp lastSignIn = rs.getTimestamp("last_sign_in_at");
        return new UserAccountResponse(
                rs.getObject("id", UUID.class),
                name == null || name.isBlank() ? null : name,
                rs.getString("email"),
                List.of(Role.EMPLOYEE),
                departmentId == null ? null : new DepartmentRefResponse(departmentId, rs.getString("department_name")),
                AccountStatus.valueOf(rs.getString("status")),
                lastSignIn == null ? null : lastSignIn.toInstant());
    }
}
