package vn.tsolve.identity.mock;

import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.port.TenantScope;

/** Interim mock — replace when KAN-275 workspace-session (identity Tasks 26a/26b) lands; never enable in prod. */
@Component
@Profile("identity-mock")
public class MockUserListSeeder implements ApplicationRunner {
    private final TenantScope scope;
    private final MockIdentityProperties properties;
    private final JdbcClient jdbc;

    public MockUserListSeeder(TenantScope scope, MockIdentityProperties properties, JdbcClient jdbc) {
        this.scope = scope;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        scope.runIn(properties.workspaceId(), () -> {
            for (String department : new String[] {"Customer Support", "IT Operations"}) {
                jdbc.sql("INSERT INTO department (name, status) VALUES (:name, 'ACTIVE') ON CONFLICT DO NOTHING")
                        .param("name", department)
                        .update();
                jdbc.sql(
                                """
                        INSERT INTO space(department_id, name, status)
                        SELECT department_id, name || ' Wiki', 'ACTIVE' FROM department WHERE name = :name
                        ON CONFLICT (department_id) DO NOTHING
                        """)
                        .param("name", department)
                        .update();
            }
            account(properties.accountEmail(), "Demo Admin", "ACTIVE", "ADMIN", null);
            account("resolver@acme.test", "Demo Resolver", "ACTIVE", "TICKET_RESOLVER", "Customer Support");
            account("manager@acme.test", "Demo Manager", "ACTIVE", "PROJECT_MANAGER", "IT Operations");
            account("knowledge@acme.test", "Demo Knowledge Manager", "ACTIVE", "KNOWLEDGE_MANAGER", null);
            account(
                    "knowledge-replacement@roles-demo.test",
                    "Demo Replacement Knowledge Manager",
                    "ACTIVE",
                    "KNOWLEDGE_MANAGER",
                    null);
            jdbc.sql(
                            """
                    INSERT INTO space_manager(space_id, user_id, assigned_at)
                    SELECT s.space_id, u.id, now() FROM space s
                    JOIN department d ON d.department_id = s.department_id
                    CROSS JOIN app_user u WHERE d.name = 'Customer Support' AND u.email = 'knowledge@acme.test'
                    ON CONFLICT (space_id) DO NOTHING
                    """)
                    .update();
            account("employee@acme.test", "Demo Employee", "ACTIVE", null, null);
            account("deactivated@acme.test", "Demo Deactivated", "DEACTIVATED", null, null);
            account("nameless@acme.test", null, "ACTIVE", null, null);
            account(
                    "manager-replacement@roles-demo.test",
                    "Demo Replacement Manager",
                    "ACTIVE",
                    "PROJECT_MANAGER",
                    "Customer Support");
            project("SUP", "Customer Support", "ACTIVE");
            project("OPS", "IT Operations", "ACTIVE");
            project("OLD", "IT Operations", "REMOVED");
            return null;
        });
    }

    private void project(String key, String department, String status) {
        jdbc.sql(
                        """
                INSERT INTO project (department_id, jira_project_key, status)
                SELECT department_id, :key, :status FROM department WHERE name = :department
                ON CONFLICT (jira_project_key) DO NOTHING
                """)
                .param("key", key)
                .param("department", department)
                .param("status", status)
                .update();
        jdbc.sql(
                        """
                INSERT INTO project_membership (user_id, project_id, role_in_project)
                SELECT u.id, p.public_id, 'PROJECT_MANAGER'
                FROM app_user u CROSS JOIN project p
                WHERE u.email = 'manager@acme.test' AND p.jira_project_key = :key
                  AND NOT EXISTS (SELECT 1 FROM project_membership m
                                  WHERE m.project_id = p.public_id AND m.role_in_project = 'PROJECT_MANAGER')
                ON CONFLICT (user_id, project_id) DO NOTHING
                """)
                .param("key", key)
                .update();
        jdbc.sql(
                        """
                INSERT INTO project_membership (user_id, project_id, role_in_project)
                SELECT u.id, p.public_id, 'MEMBER'
                FROM app_user u CROSS JOIN project p
                WHERE u.email = 'resolver@acme.test' AND p.jira_project_key = :key
                ON CONFLICT (user_id, project_id) DO NOTHING
                """)
                .param("key", key)
                .update();
    }

    private void account(String email, String name, String status, String role, String department) {
        String normalized = email.toLowerCase(Locale.ROOT);
        jdbc.sql(
                        """
                INSERT INTO app_user (email, full_name, status, created_at, last_sign_in_at, department_id)
                VALUES (:email, :name, :status, now(), CASE WHEN :name IS NULL THEN NULL ELSE now() END,
                        (SELECT department_id FROM department WHERE name = :department))
                ON CONFLICT (email) DO NOTHING
                """)
                .param("email", normalized)
                .param("name", name, java.sql.Types.VARCHAR)
                .param("status", status)
                .param("department", department, java.sql.Types.VARCHAR)
                .update();
        if (role != null) {
            UUID id = jdbc.sql("SELECT id FROM app_user WHERE email = :email")
                    .param("email", normalized)
                    .query(UUID.class)
                    .single();
            jdbc.sql(
                            "INSERT INTO user_role (user_id, role, assigned_at) VALUES (:id, :role, now()) ON CONFLICT (user_id, role) DO NOTHING")
                    .param("id", id)
                    .param("role", role)
                    .update();
        }
    }
}
