package vn.tsolve.project.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tsolve.project.dto.ProjectResponse;
import vn.tsolve.project.dto.ProjectRowResponse;
import vn.tsolve.project.dto.ProjectRowResponse.ProjectManagerRef;

@Repository
public class ProjectLinkRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ProjectLinkRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record CallerDepartment(long departmentId, UUID departmentPublicId, String name) {}

    public record Link(
            UUID projectId,
            long departmentId,
            String departmentName,
            String jiraProjectKey,
            String status,
            List<String> doneStatusIds,
            Integer reviewRange,
            UUID managerId,
            boolean managerProtected) {}

    public Optional<CallerDepartment> findCallerDepartment(UUID callerId) {
        return jdbc
                .query(
                        """
                        SELECT d.department_id, d.public_id, d.name
                        FROM app_user u JOIN department d ON d.department_id = u.department_id
                        WHERE u.id = :callerId AND u.status = 'ACTIVE' AND d.status = 'ACTIVE'
                          AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROJECT_MANAGER')
                        """,
                        Map.of("callerId", callerId),
                        (rs, rowNum) -> new CallerDepartment(
                                rs.getLong("department_id"),
                                rs.getObject("public_id", UUID.class),
                                rs.getString("name")))
                .stream()
                .findFirst();
    }

    public Optional<Link> findLink(String jiraProjectKey) {
        return jdbc
                .query(
                        """
                        SELECT p.public_id, p.department_id, d.name AS department_name,
                               p.jira_project_key, p.status, p.done_status::text AS done_status,
                               p.review_range, m.user_id AS manager_id,
                               COALESCE(u.status = 'ACTIVE' AND u.department_id = p.department_id AND EXISTS (
                                   SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROJECT_MANAGER'
                               ), false) AS manager_protected
                        FROM project p JOIN department d ON d.department_id = p.department_id
                        LEFT JOIN project_membership m ON m.project_id = p.public_id AND m.role_in_project = 'PROJECT_MANAGER'
                        LEFT JOIN app_user u ON u.id = m.user_id
                        WHERE p.jira_project_key = :key
                        """,
                        Map.of("key", jiraProjectKey),
                        (rs, rowNum) -> new Link(
                                rs.getObject("public_id", UUID.class),
                                rs.getLong("department_id"),
                                rs.getString("department_name"),
                                rs.getString("jira_project_key"),
                                rs.getString("status"),
                                readDoneStatusIds(rs.getString("done_status")),
                                rs.getObject("review_range", Integer.class),
                                rs.getObject("manager_id", UUID.class),
                                rs.getBoolean("manager_protected")))
                .stream()
                .findFirst();
    }

    /** Row lock for the write transaction; a caller re-reads the link afterwards, since joined rows are not re-read. */
    public void lockProject(UUID projectId) {
        jdbc.query(
                "SELECT public_id FROM project WHERE public_id = :id FOR UPDATE",
                Map.of("id", projectId),
                (rs, rowNum) -> rs.getObject(1, UUID.class));
    }

    /** Empty when another Project already holds the key: a lost insert, never turned into a reconnect (D22). */
    public Optional<UUID> insertProject(
            long departmentId, String jiraProjectKey, List<String> doneStatusIds, int reviewRange) {
        return jdbc
                .query(
                        """
                        INSERT INTO project (department_id, jira_project_key, done_status, review_range, status, field_mapping)
                        VALUES (:departmentId, :key, CAST(:ids AS jsonb), :range, 'ACTIVE', NULL)
                        ON CONFLICT (jira_project_key) DO NOTHING
                        RETURNING public_id
                        """,
                        new MapSqlParameterSource()
                                .addValue("departmentId", departmentId)
                                .addValue("key", jiraProjectKey)
                                .addValue("ids", writeDoneStatusIds(doneStatusIds))
                                .addValue("range", reviewRange),
                        (rs, rowNum) -> rs.getObject(1, UUID.class))
                .stream()
                .findFirst();
    }

    /**
     * Reconnects a Removed row in place with Jira's canonical key and the saved settings; Department, tickets and
     * {@code field_mapping} stay. Zero when the row is no longer Removed: the caller lost the race (D22).
     */
    public int reconnectProject(UUID projectId, String jiraProjectKey, List<String> doneStatusIds, int reviewRange) {
        return jdbc.update(
                """
                UPDATE project SET status = 'ACTIVE', jira_project_key = :key,
                    done_status = CAST(:ids AS jsonb), review_range = :range
                WHERE public_id = :id AND status = 'REMOVED'
                """,
                new MapSqlParameterSource()
                        .addValue("id", projectId)
                        .addValue("key", jiraProjectKey)
                        .addValue("ids", writeDoneStatusIds(doneStatusIds))
                        .addValue("range", reviewRange));
    }

    /** Removes only the previous manager's row, so the caller can take the one manager place (AC-003.4). */
    public void deleteManager(UUID projectId, UUID managerId) {
        jdbc.update("""
                DELETE FROM project_membership
                WHERE project_id = :id AND user_id = :managerId AND role_in_project = 'PROJECT_MANAGER'
                """, Map.of("id", projectId, "managerId", managerId));
    }

    public void upsertManager(UUID callerId, UUID projectId) {
        jdbc.update("""
                INSERT INTO project_membership (user_id, project_id, role_in_project)
                VALUES (:callerId, :id, 'PROJECT_MANAGER')
                ON CONFLICT (user_id, project_id) DO UPDATE SET role_in_project = 'PROJECT_MANAGER'
                """, Map.of("callerId", callerId, "id", projectId));
    }

    public Optional<ProjectResponse> readProject(UUID projectId) {
        return jdbc
                .query(
                        """
                        SELECT p.public_id, d.public_id AS department_public_id, p.jira_project_key,
                               p.done_status::text AS done_status, p.review_range, p.status
                        FROM project p JOIN department d ON d.department_id = p.department_id
                        WHERE p.public_id = :id
                        """,
                        Map.of("id", projectId),
                        (rs, rowNum) -> new ProjectResponse(
                                rs.getObject("public_id", UUID.class),
                                rs.getObject("department_public_id", UUID.class),
                                rs.getString("jira_project_key"),
                                readDoneStatusIds(rs.getString("done_status")),
                                rs.getInt("review_range"),
                                rs.getString("status")))
                .stream()
                .findFirst();
    }

    /**
     * All Active and Removed Projects of one Department, by key in code-point order (keys are {@code [A-Z0-9_]}, so
     * {@code COLLATE "C"} does not depend on the database locale). The manager is shown only while the account is
     * Active; the count covers every membership row, whatever the account status (AC-004.4 to AC-004.6).
     */
    public List<ProjectRowResponse> listProjects(long departmentId) {
        return jdbc.query("""
                SELECT p.public_id, p.jira_project_key, p.status, u.id AS manager_id, u.full_name AS manager_name,
                       (SELECT count(*) FROM project_membership a WHERE a.project_id = p.public_id) AS member_count
                FROM project p
                LEFT JOIN project_membership m ON m.project_id = p.public_id AND m.role_in_project = 'PROJECT_MANAGER'
                LEFT JOIN app_user u ON u.id = m.user_id AND u.status = 'ACTIVE'
                WHERE p.department_id = :departmentId
                ORDER BY p.jira_project_key COLLATE "C"
                """, Map.of("departmentId", departmentId), (rs, rowNum) -> {
            UUID managerId = rs.getObject("manager_id", UUID.class);
            return new ProjectRowResponse(
                    rs.getObject("public_id", UUID.class),
                    rs.getString("jira_project_key"),
                    managerId == null ? null : new ProjectManagerRef(managerId, rs.getString("manager_name")),
                    rs.getInt("member_count"),
                    rs.getString("status"));
        });
    }

    private String writeDoneStatusIds(List<String> ids) {
        try {
            return mapper.writeValueAsString(ids);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Done status ids are not serializable", error);
        }
    }

    private List<String> readDoneStatusIds(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid stored Done status ids", error);
        }
    }
}
