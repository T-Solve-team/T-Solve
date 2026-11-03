package vn.tsolve.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.Set;
import java.util.stream.Collectors;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

public class SqlBootstrapIT {

    @Test
    void
            bootstrapsOnlySuppliedSql_AC_001_15_AC_001_16_AC_001_17_AC_001_18_AC_001_19_AC_001_20_AC_001_21_AC_001_22_AC_001_23_AC_001_27_AC_002_28()
                    throws Exception {
        assertThat(getClass().getResource("/db/changelog/catalog/catalog-master.xml"))
                .isNotNull();
        assertThat(getClass().getResource("/db/changelog/tenant/tenant-master.xml"))
                .isNotNull();
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            try (var owner = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                owner.createStatement().execute("CREATE DATABASE sql_tenant");
            }
            migrate(db.getJdbcUrl(), db, "catalog");
            migrate(db.getJdbcUrl(), db, "catalog");
            var catalog = jdbc(db.getJdbcUrl(), db);
            assertThat(tables(catalog)).containsExactlyInAnyOrder("tenant_workspace", "workspace_email");
            assertThat(catalog.queryForObject("SELECT count(*) FROM tenant_workspace", Long.class))
                    .isZero();
            catalog.update("INSERT INTO tenant_workspace (site_name, db_host, db_name, db_username, db_password_ref)"
                    + " VALUES ('one', 'db', 'one', 'one_app', 'TENANT_ONE_PASSWORD')");
            assertThat(catalog.queryForObject("SELECT workspace_id FROM tenant_workspace", Long.class))
                    .isEqualTo(1L);
            assertThatThrownBy(() -> catalog.update("INSERT INTO workspace_email VALUES ('UPPER@example.test', 1)"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            String tenantUrl = db.getJdbcUrl().replace("/" + db.getDatabaseName(), "/sql_tenant");
            migrate(tenantUrl, db, "tenant");
            migrate(tenantUrl, db, "tenant");
            var tenant = jdbc(tenantUrl, db);
            assertThat(tables(tenant))
                    .containsExactlyInAnyOrder(
                            "workspace",
                            "department",
                            "app_user",
                            "user_role",
                            "project",
                            "project_membership",
                            "import_history",
                            "ticket",
                            "evidence",
                            "review_decision",
                            "draft",
                            "feedback",
                            "suggestion_result",
                            "search_log",
                            "audit_log_entry",
                            "chat_session",
                            "chat_message",
                            "chat_feedback",
                            "space",
                            "space_manager",
                            "space_permission",
                            "folder",
                            "wiki_document");
            assertThat(tenant.queryForList(
                            "SELECT column_name FROM information_schema.columns"
                                    + " WHERE table_schema='public' AND table_name='app_user'",
                            String.class))
                    .containsExactlyInAnyOrder(
                            "user_id",
                            "department_id",
                            "full_name",
                            "company_email",
                            "linked_jira_account_id",
                            "account_status");
            tenant.update("INSERT INTO app_user(full_name, company_email) VALUES ('Admin', 'admin@example.test')");
            assertThat(tenant.queryForObject("SELECT user_id FROM app_user", Long.class))
                    .isEqualTo(1L);
            assertThat(tenant.queryForObject("SELECT count(*) FROM user_role", Long.class))
                    .isZero();
            assertThatThrownBy(() -> tenant.update("INSERT INTO user_role(user_id,role) VALUES(1,'EMPLOYEE')"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> tenant.update("INSERT INTO user_role(user_id,role) VALUES(999,'ADMIN')"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            tenant.update("INSERT INTO user_role(user_id,role) VALUES(1,'ADMIN')");
            assertThatThrownBy(() -> tenant.update("INSERT INTO user_role(user_id,role) VALUES(1,'ADMIN')"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    public static JdbcTemplate jdbc(String url, PostgreSQLContainer<?> db) {
        return new JdbcTemplate(new DriverManagerDataSource(url, db.getUsername(), db.getPassword()));
    }

    public static void migrate(String url, PostgreSQLContainer<?> db, String kind) throws Exception {
        try (var connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
                var liquibase = new Liquibase(
                        "db/changelog/" + kind + "/" + kind + "-master.xml",
                        new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection))) {
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    private static Set<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'", String.class).stream()
                .filter(name -> !name.startsWith("databasechangelog"))
                .collect(Collectors.toSet());
    }
}
