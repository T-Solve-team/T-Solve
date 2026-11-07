package vn.tsolve.identity.repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.dto.SpaceRefResponse;

@Repository
public class UserCreationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public UserCreationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Space(long id, UUID publicId, String name) {}

    public Optional<String> accountStatus(String email) {
        return jdbc
                .query(
                        "SELECT status FROM app_user WHERE email = :email",
                        Map.of("email", email),
                        (rs, row) -> rs.getString("status"))
                .stream()
                .findFirst();
    }

    public Optional<Long> activeDepartment(UUID publicId) {
        return jdbc
                .query(
                        "SELECT department_id FROM department WHERE public_id = :id AND status = 'ACTIVE'",
                        Map.of("id", publicId),
                        (rs, row) -> rs.getLong("department_id"))
                .stream()
                .findFirst();
    }

    public List<SpaceRefResponse> activeSpaces() {
        return jdbc.query(
                "SELECT public_id, name FROM space WHERE status = 'ACTIVE' ORDER BY lower(name), space_id",
                Map.of(),
                (rs, row) -> new SpaceRefResponse(rs.getObject("public_id", UUID.class), rs.getString("name")));
    }

    /** Stable lock order prevents deadlocks when creations request overlapping sets of Spaces. */
    public List<Space> lockActiveSpaces(List<UUID> publicIds) {
        if (publicIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT space_id, public_id, name FROM space WHERE public_id IN (:ids) AND status = 'ACTIVE' ORDER BY space_id FOR UPDATE",
                Map.of("ids", publicIds),
                (rs, row) ->
                        new Space(rs.getLong("space_id"), rs.getObject("public_id", UUID.class), rs.getString("name")));
    }

    public boolean isOccupied(long spaceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM space_manager WHERE space_id = :id)",
                Map.of("id", spaceId),
                Boolean.class));
    }

    /** A targeted conflict clause leaves the transaction usable and does not hide other constraints. */
    public boolean insertAccount(UUID id, String email, Long departmentId) {
        return jdbc.update(
                        """
                INSERT INTO app_user(id, email, full_name, status, created_at, department_id)
                VALUES (:id, :email, NULL, 'ACTIVE', now(), :department)
                ON CONFLICT (email) DO NOTHING
                """,
                        new MapSqlParameterSource("id", id)
                                .addValue("email", email)
                                .addValue("department", departmentId, Types.BIGINT))
                == 1;
    }

    public void insertRoles(UUID id, List<Role> roles) {
        for (Role role : roles) {
            if (role != Role.EMPLOYEE) {
                jdbc.update(
                        "INSERT INTO user_role(user_id, role, assigned_at) VALUES (:id, :role, now())",
                        Map.of("id", id, "role", role.name()));
            }
        }
    }

    public boolean insertManager(long spaceId, UUID userId) {
        return jdbc.update(
                        """
                INSERT INTO space_manager(space_id, user_id, assigned_at) VALUES (:space, :user, now())
                ON CONFLICT (space_id) DO NOTHING
                """,
                        Map.of("space", spaceId, "user", userId))
                == 1;
    }
}
