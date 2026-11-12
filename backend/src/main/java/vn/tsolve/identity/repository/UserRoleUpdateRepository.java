package vn.tsolve.identity.repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.account.AccountStatus;

@Repository
public class UserRoleUpdateRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public UserRoleUpdateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void serializeUpdates() {
        // Target-first row locking alone deadlocks for mutual Admin removal and cyclic handovers.
        // Advisory locks are database-local: independent Workspace databases never block each other.
        jdbc.query("SELECT pg_advisory_xact_lock(110, 7)", Map.of(), (rs, row) -> true);
    }

    public boolean lockTarget(UUID id) {
        return !jdbc.query(
                        "SELECT id FROM app_user WHERE id = :id FOR UPDATE",
                        Map.of("id", id),
                        (rs, row) -> rs.getObject(1, UUID.class))
                .isEmpty();
    }

    public Optional<Long> activeDepartment(UUID publicId) {
        return jdbc
                .query(
                        "SELECT department_id FROM department WHERE public_id = :id AND status = 'ACTIVE'",
                        Map.of("id", publicId),
                        (rs, row) -> rs.getLong(1))
                .stream()
                .findFirst();
    }

    public record Space(UUID id, String name, boolean active, UUID managerId) {}

    public List<Space> spaces() {
        return jdbc.query(
                """
                SELECT s.public_id, s.name, s.status, sm.user_id FROM space s
                LEFT JOIN space_manager sm ON sm.space_id = s.space_id ORDER BY lower(s.name), s.public_id
                """,
                Map.of(),
                (rs, row) -> new Space(
                        rs.getObject("public_id", UUID.class),
                        rs.getString("name"),
                        "ACTIVE".equals(rs.getString("status")),
                        rs.getObject("user_id", UUID.class)));
    }

    public void lockSpaces(Collection<UUID> ids) {
        if (!ids.isEmpty()) {
            jdbc.query(
                    "SELECT public_id FROM space WHERE public_id IN (:ids) ORDER BY public_id FOR UPDATE",
                    Map.of("ids", ids),
                    (rs, row) -> rs.getObject(1, UUID.class));
        }
    }

    public void lockActiveAdmins() {
        jdbc.query(
                """
                SELECT u.id FROM app_user u WHERE u.status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'ADMIN')
                ORDER BY u.id FOR UPDATE OF u
                """,
                Map.of(),
                (rs, row) -> rs.getObject(1, UUID.class));
    }

    public long activeAdminCount() {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM app_user u WHERE u.status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'ADMIN')
                """,
                Map.of(),
                Long.class);
    }

    public void lockRecipients(Collection<UUID> ids) {
        if (!ids.isEmpty()) {
            jdbc.query(
                    "SELECT id FROM app_user WHERE id IN (:ids) ORDER BY id FOR UPDATE",
                    Map.of("ids", ids),
                    (rs, row) -> rs.getObject(1, UUID.class));
        }
    }

    public void transferProject(UUID userId, UUID projectId, UUID recipientId, boolean retainMember) {
        if (retainMember) {
            jdbc.update(
                    "UPDATE project_membership SET role_in_project = 'MEMBER' WHERE user_id = :user AND project_id = :project",
                    Map.of("user", userId, "project", projectId));
        } else {
            jdbc.update(
                    "DELETE FROM project_membership WHERE user_id = :user AND project_id = :project",
                    Map.of("user", userId, "project", projectId));
        }
        jdbc.update(
                """
                INSERT INTO project_membership(user_id, project_id, role_in_project) VALUES (:user, :project, 'PROJECT_MANAGER')
                ON CONFLICT (user_id, project_id) DO UPDATE SET role_in_project = 'PROJECT_MANAGER'
                """,
                Map.of("user", recipientId, "project", projectId));
    }

    public void transferSpace(UUID spaceId, UUID recipientId) {
        jdbc.update(
                """
                UPDATE space_manager SET user_id = :user, assigned_at = now()
                WHERE space_id = (SELECT space_id FROM space WHERE public_id = :space)
                """,
                Map.of("user", recipientId, "space", spaceId));
    }

    public void removeMembers(UUID userId) {
        jdbc.update(
                "DELETE FROM project_membership WHERE user_id = :user AND role_in_project = 'MEMBER'",
                Map.of("user", userId));
    }

    public void updateStatus(UUID userId, AccountStatus status) {
        jdbc.update(
                "UPDATE app_user SET status = :status WHERE id = :id", Map.of("id", userId, "status", status.name()));
    }

    public void replaceAssignments(UUID userId, Collection<Role> roles, Long departmentId, Collection<UUID> spaces) {
        jdbc.update("DELETE FROM user_role WHERE user_id = :user", Map.of("user", userId));
        for (Role role : roles) {
            if (role != Role.EMPLOYEE) {
                jdbc.update(
                        "INSERT INTO user_role(user_id, role, assigned_at) VALUES (:user, :role, now())",
                        Map.of("user", userId, "role", role.name()));
            }
        }
        var department = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("user", userId)
                .addValue("department", departmentId, java.sql.Types.BIGINT);
        jdbc.update("UPDATE app_user SET department_id = :department WHERE id = :user", department);
        for (UUID space : spaces) {
            String name = jdbc.queryForObject(
                    "SELECT name FROM space WHERE public_id = :space", Map.of("space", space), String.class);
            try {
                jdbc.update(
                        """
                    INSERT INTO space_manager(space_id, user_id, assigned_at)
                    SELECT space_id, :user, now() FROM space WHERE public_id = :space
                    AND NOT EXISTS (SELECT 1 FROM space_manager sm WHERE sm.space_id = space.space_id AND sm.user_id = :user)
                    """,
                        Map.of("user", userId, "space", space));
            } catch (DataIntegrityViolationException failure) {
                if (failure.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new BusinessRuleException("MSG117", Map.of("space_name", name), "spaceIds");
                }
                throw failure;
            }
        }
    }
}
