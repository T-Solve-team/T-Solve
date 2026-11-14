package vn.tsolve.platform.isolation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.common.security.Role;
import vn.tsolve.identity.service.ScopeAccessService;
import vn.tsolve.search.embedding.repository.PgVector;
import vn.tsolve.search.scope.service.VisibleScope;

/** JDBC seeds go directly to each tenant; the visibility adapter uses the application's routed JDBC. */
final class IsolationFixtures {
    static final String TITLE = "Isolation VPN connection failure";
    static final String QUESTION = "How do I fix the Isolation VPN connection failure?";

    private IsolationFixtures() {}

    record TenantData(
            long department, UUID resolver, UUID manager, UUID project, UUID published, UUID pending, String marker) {}

    static float[] vector() {
        float[] vector = new float[EmbeddingClient.DIMENSIONS];
        vector[0] = 1;
        return vector;
    }

    static TenantData seed(JdbcTemplate db, String tenant) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String marker = tenant + "_PRIVATE_" + suffix;
        long department = db.queryForObject(
                "INSERT INTO department(name,status) VALUES (?,'ACTIVE') RETURNING department_id", Long.class, marker);
        UUID project = db.queryForObject(
                "INSERT INTO project(department_id,jira_project_key,status) VALUES (?,?,'ACTIVE') RETURNING public_id",
                UUID.class,
                department,
                tenant + suffix);
        UUID resolver = account(db, department, marker.toLowerCase() + "-resolver@example.test", "TICKET_RESOLVER");
        UUID manager = account(db, department, marker.toLowerCase() + "-manager@example.test", "PROJECT_MANAGER");
        db.update(
                "INSERT INTO project_membership(user_id,project_id,role_in_project) VALUES (?,?,'MEMBER'), (?,?,'PROJECT_MANAGER')",
                resolver,
                project,
                manager,
                project);
        UUID published = ticket(db, project, marker, "PUBLISHED");
        UUID pending = ticket(db, project, marker, "PENDING_REVIEW");
        db.update(
                "INSERT INTO ticket_embedding(ticket_id,embedding,model) VALUES (?,CAST(? AS vector),'bge-m3')",
                published,
                PgVector.literal(vector()));
        return new TenantData(department, resolver, manager, project, published, pending, marker);
    }

    private static UUID account(JdbcTemplate db, long department, String email, String role) {
        UUID id = db.queryForObject(
                "INSERT INTO app_user(email,full_name,status,created_at,department_id) VALUES (?,?,'ACTIVE',now(),?) RETURNING id",
                UUID.class,
                email,
                email,
                department);
        db.update("INSERT INTO user_role(user_id,role,assigned_at) VALUES (?,?,now())", id, role);
        return id;
    }

    private static UUID ticket(JdbcTemplate db, UUID project, String marker, String status) {
        UUID id = UUID.randomUUID();
        db.update(
                """
                INSERT INTO ticket(ticket_id,public_id,source_ticket_id,project_id,source,status,title,writer,
                    symptoms,root_cause,resolution_steps,scope,review_due_date,review_queue_entered_at)
                VALUES (?,?,?,?,'SOLUTION_FORM',?,?,?, ?,?,?,?,current_date + 30,now())
                """,
                id,
                id,
                marker + "-" + status,
                project,
                status,
                TITLE,
                marker + " writer",
                marker + " symptoms",
                marker + " root cause",
                marker + " resolution",
                marker + " scope");
        return id;
    }

    /** Snapshot every application table, including audit tables, to catch unexpected writes on rejected requests. */
    static Map<String, Long> rowCounts(JdbcTemplate db) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : db.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name NOT IN ('databasechangelog', 'databasechangeloglock')
                ORDER BY table_name
                """, String.class)) {
            counts.put(
                    table,
                    db.queryForObject("SELECT count(*) FROM \"" + table.replace("\"", "\"\"") + "\"", Long.class));
        }
        return counts;
    }

    static void clean(JdbcTemplate db, TenantData data) {
        if (data == null) return;
        db.update("DELETE FROM search_log WHERE user_id IN (?,?)", data.resolver(), data.manager());
        db.update("DELETE FROM chatbot_answer WHERE user_id IN (?,?)", data.resolver(), data.manager());
        db.update("DELETE FROM ticket_feedback WHERE ticket_id IN (?,?)", data.published(), data.pending());
        db.update("DELETE FROM ticket_embedding WHERE ticket_id IN (?,?)", data.published(), data.pending());
        db.update("DELETE FROM ticket WHERE ticket_id IN (?,?)", data.published(), data.pending());
        db.update("DELETE FROM project_membership WHERE project_id = ?", data.project());
        db.update("DELETE FROM user_role WHERE user_id IN (?,?)", data.resolver(), data.manager());
        db.update("DELETE FROM app_user WHERE id IN (?,?)", data.resolver(), data.manager());
        db.update("DELETE FROM project WHERE public_id = ?", data.project());
        db.update("DELETE FROM department WHERE department_id = ?", data.department());
    }

    /**
     * Search's production VisibleScope currently fails closed pending identity integration. Supply that seam only:
     * discover projects in the bound database, then validate membership through the real ScopeAccessService.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ScopeConfiguration {
        @Bean
        @Primary
        VisibleScope isolationVisibleScope(JdbcTemplate jdbc, ScopeAccessService access) {
            return user -> {
                Set<Long> departments = new HashSet<>();
                Set<Long> managed = new HashSet<>();
                List<Map<String, Object>> projects = jdbc.queryForList(
                        "SELECT project_id, public_id, department_id FROM project WHERE status = 'ACTIVE'");
                for (Map<String, Object> project : projects) {
                    if (access.canAccessProject(user.id(), (UUID) project.get("public_id"))) {
                        departments.add(((Number) project.get("department_id")).longValue());
                        if (user.roles().contains(Role.PROJECT_MANAGER)) {
                            managed.add(((Number) project.get("project_id")).longValue());
                        }
                    }
                }
                return new VisibleScope.Scope(departments, managed, Optional.empty());
            };
        }
    }
}
