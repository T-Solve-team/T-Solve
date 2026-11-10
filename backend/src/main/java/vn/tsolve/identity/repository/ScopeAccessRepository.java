package vn.tsolve.identity.repository;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScopeAccessRepository {
    private static final String COUNTED_MEMBERSHIP =
            """
            ((m.role_in_project = 'PROJECT_MANAGER' AND EXISTS (
                SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROJECT_MANAGER'))
             OR (m.role_in_project = 'MEMBER' AND EXISTS (
                SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'TICKET_RESOLVER')))
            """;

    private static final String SCOPED_DEPARTMENTS =
            """
            SELECT u.department_id WHERE EXISTS (
                SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROJECT_MANAGER')
            UNION
            SELECT p.department_id FROM project_membership m
            JOIN project p ON p.public_id = m.project_id
            WHERE m.user_id = u.id AND
            """
                    + COUNTED_MEMBERSHIP;

    private static final String DEPARTMENT =
            """
            SELECT EXISTS (
                SELECT 1 FROM app_user u
                JOIN department d ON d.public_id = :departmentId AND d.status = 'ACTIVE'
                WHERE u.id = :accountId AND u.status = 'ACTIVE'
                  AND d.department_id IN (
            """
                    + SCOPED_DEPARTMENTS + "))";

    private static final String PROJECT =
            """
            SELECT EXISTS (
                SELECT 1 FROM app_user u
                JOIN project_membership m ON m.user_id = u.id
                JOIN project p ON p.public_id = m.project_id
                JOIN department d ON d.department_id = p.department_id AND d.status = 'ACTIVE'
                WHERE u.id = :accountId AND u.status = 'ACTIVE' AND p.public_id = :projectId
                  AND
            """
                    + COUNTED_MEMBERSHIP + ")";

    private static final String SPACE =
            """
            SELECT EXISTS (
                SELECT 1 FROM app_user u
                JOIN space s ON s.public_id = :spaceId AND s.status = 'ACTIVE'
                JOIN department d ON d.department_id = s.department_id AND d.status = 'ACTIVE'
                WHERE u.id = :accountId AND u.status = 'ACTIVE'
                  AND (EXISTS (
                      SELECT 1 FROM space_manager sm
                      JOIN user_role r ON r.user_id = sm.user_id AND r.role = 'KNOWLEDGE_MANAGER'
                      WHERE sm.space_id = s.space_id AND sm.user_id = u.id)
                    OR s.department_id IN (
            """
                    + SCOPED_DEPARTMENTS + ")))";

    private final NamedParameterJdbcTemplate jdbc;

    public ScopeAccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean department(UUID accountId, UUID departmentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                DEPARTMENT, Map.of("accountId", accountId, "departmentId", departmentId), Boolean.class));
    }

    public boolean project(UUID accountId, UUID projectId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(PROJECT, Map.of("accountId", accountId, "projectId", projectId), Boolean.class));
    }

    public boolean space(UUID accountId, UUID spaceId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(SPACE, Map.of("accountId", accountId, "spaceId", spaceId), Boolean.class));
    }
}
