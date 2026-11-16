package vn.tsolve.wiki.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.wiki.dto.WikiPermissionCandidates;
import vn.tsolve.wiki.dto.WikiSpaceSettings;
import vn.tsolve.wiki.repository.AccessRepository.AccessSql;

/** Permission projections use read-only joins to identity/workspace tables, as WikiSearchRepository does (W10). */
@Repository
public class WikiPermissionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public WikiPermissionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findActiveSpace(UUID spaceId) {
        return jdbc
                .query(
                        "SELECT s.space_id FROM space s WHERE s.public_id = :spaceId AND " + AccessSql.ACTIVE_SPACE,
                        Map.of("spaceId", spaceId),
                        (row, index) -> row.getLong("space_id"))
                .stream()
                .findFirst();
    }

    public WikiSpaceSettings getSettings(long spaceId) {
        var header = jdbc.queryForObject(
                """
                SELECT s.public_id, s.name, d.public_id AS department_public_id, d.name AS department_name,
                       km.full_name AS knowledge_manager
                FROM space s JOIN department d ON d.department_id = s.department_id
                LEFT JOIN space_manager sm ON sm.space_id = s.space_id
                LEFT JOIN app_user km ON km.id = sm.user_id AND km.status = 'ACTIVE'
                    AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = km.id AND r.role = 'KNOWLEDGE_MANAGER')
                WHERE s.space_id = :spaceId
                """,
                Map.of("spaceId", spaceId),
                (row, index) -> new WikiSpaceSettings(
                        row.getObject("public_id", UUID.class),
                        row.getString("name"),
                        row.getString("department_name"),
                        row.getString("knowledge_manager"),
                        new WikiSpaceSettings.OwnDepartment(
                                row.getObject("department_public_id", UUID.class),
                                row.getString("department_name"),
                                true),
                        List.of(),
                        List.of()));
        return new WikiSpaceSettings(
                header.spaceId(),
                header.name(),
                header.departmentName(),
                header.knowledgeManager(),
                header.ownDepartment(),
                listDepartments(spaceId),
                listUsers(spaceId));
    }

    private List<WikiSpaceSettings.DepartmentPermission> listDepartments(long spaceId) {
        return jdbc.query(
                """
                SELECT p.space_permission_id, d.public_id, d.name, actor.full_name AS granted_by, p.granted_at
                FROM space_permission p JOIN department d ON d.department_id = p.department_id
                JOIN app_user actor ON actor.id = p.granted_by
                WHERE p.space_id = :spaceId ORDER BY d.name, d.public_id
                """,
                Map.of("spaceId", spaceId),
                (row, index) -> new WikiSpaceSettings.DepartmentPermission(
                        row.getObject("space_permission_id", UUID.class),
                        row.getObject("public_id", UUID.class),
                        row.getString("name"),
                        row.getString("granted_by"),
                        row.getTimestamp("granted_at").toInstant()));
    }

    private List<WikiSpaceSettings.UserPermission> listUsers(long spaceId) {
        return jdbc.query(
                """
                SELECT p.space_permission_id, u.id, u.full_name, u.email, d.name AS home_department,
                       u.status = 'ACTIVE' AS active, actor.full_name AS granted_by, p.granted_at
                FROM space_permission p JOIN app_user u ON u.id = p.user_id
                LEFT JOIN department d ON d.department_id = u.department_id
                JOIN app_user actor ON actor.id = p.granted_by
                WHERE p.space_id = :spaceId ORDER BY u.full_name, u.id
                """,
                Map.of("spaceId", spaceId),
                (row, index) -> new WikiSpaceSettings.UserPermission(
                        row.getObject("space_permission_id", UUID.class),
                        row.getObject("id", UUID.class),
                        row.getString("full_name"),
                        row.getString("email"),
                        row.getString("home_department"),
                        row.getBoolean("active"),
                        row.getString("granted_by"),
                        row.getTimestamp("granted_at").toInstant()));
    }

    public WikiPermissionCandidates listCandidates(long spaceId) {
        var departments = jdbc.query(
                """
                SELECT d.public_id, d.name FROM department d
                WHERE d.status = 'ACTIVE'
                  AND d.department_id <> (SELECT s.department_id FROM space s WHERE s.space_id = :spaceId)
                  AND NOT EXISTS (SELECT 1 FROM space_permission p WHERE p.space_id = :spaceId AND p.department_id = d.department_id)
                ORDER BY d.name, d.public_id
                """,
                Map.of("spaceId", spaceId),
                (row, index) -> new WikiPermissionCandidates.Department(
                        row.getObject("public_id", UUID.class), row.getString("name")));
        var users = jdbc.query(
                """
                SELECT u.id, u.full_name, u.email, d.name AS home_department
                FROM app_user u LEFT JOIN department d ON d.department_id = u.department_id
                WHERE u.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM space_permission p WHERE p.space_id = :spaceId AND p.user_id = u.id)
                ORDER BY u.full_name, u.id
                """,
                Map.of("spaceId", spaceId),
                (row, index) -> new WikiPermissionCandidates.User(
                        row.getObject("id", UUID.class),
                        row.getString("full_name"),
                        row.getString("email"),
                        row.getString("home_department")));
        return new WikiPermissionCandidates(departments, users);
    }

    public Optional<Long> findActiveDepartment(long spaceId, UUID departmentId) {
        return jdbc
                .query(
                        """
                SELECT d.department_id FROM department d WHERE d.public_id = :departmentId AND d.status = 'ACTIVE'
                  AND d.department_id <> (SELECT s.department_id FROM space s WHERE s.space_id = :spaceId)
                """,
                        Map.of("spaceId", spaceId, "departmentId", departmentId),
                        (row, index) -> row.getLong("department_id"))
                .stream()
                .findFirst();
    }

    public boolean isActiveUser(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM app_user WHERE id = :userId AND status = 'ACTIVE')",
                Map.of("userId", userId),
                Boolean.class));
    }

    public int insertDepartment(long spaceId, long departmentId, UUID actor, Instant now) {
        return jdbc.update(
                """
                INSERT INTO space_permission(space_id, department_id, granted_by, granted_at)
                VALUES (:spaceId, :departmentId, :actor, :now) ON CONFLICT DO NOTHING
                """,
                Map.of("spaceId", spaceId, "departmentId", departmentId, "actor", actor, "now", Timestamp.from(now)));
    }

    public int insertUser(long spaceId, UUID userId, UUID actor, Instant now) {
        return jdbc.update(
                """
                INSERT INTO space_permission(space_id, user_id, granted_by, granted_at)
                VALUES (:spaceId, :userId, :actor, :now) ON CONFLICT DO NOTHING
                """, Map.of("spaceId", spaceId, "userId", userId, "actor", actor, "now", Timestamp.from(now)));
    }

    public Optional<Permission> findPermission(UUID permissionId) {
        return jdbc
                .query(
                        """
                SELECT s.public_id AS space_public_id,
                       CASE WHEN p.department_id IS NOT NULL THEN 'DEPARTMENT' ELSE 'USER' END AS grantee_type,
                       COALESCE(d.public_id, p.user_id) AS grantee_id
                FROM space_permission p JOIN space s ON s.space_id = p.space_id
                LEFT JOIN department d ON d.department_id = p.department_id
                WHERE p.space_permission_id = :permissionId
                """,
                        Map.of("permissionId", permissionId),
                        (row, index) -> new Permission(
                                row.getObject("space_public_id", UUID.class),
                                row.getString("grantee_type"),
                                row.getObject("grantee_id", UUID.class)))
                .stream()
                .findFirst();
    }

    public int deletePermission(UUID permissionId) {
        return jdbc.update(
                "DELETE FROM space_permission WHERE space_permission_id = :permissionId",
                Map.of("permissionId", permissionId));
    }

    public record Permission(UUID spaceId, String granteeType, UUID granteeId) {}
}
