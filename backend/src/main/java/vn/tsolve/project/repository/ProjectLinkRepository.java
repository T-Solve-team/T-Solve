package vn.tsolve.project.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
