package vn.tsolve.identity;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.tsolve.common.tenant.TenantContext;

/** Test-only platform adapter and UUID schema; no production router or migration is provided by identity. */
public final class UserListTestDatabase {
    public static final UUID A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    public static final UUID B = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000002");
    public static final UUID ADMIN = id(1);
    public static final UUID EMPLOYEE = id(2);
    public static final UUID DEPARTMENT = UUID.fromString("dddddddd-0000-4000-8000-000000000001");
    public static final UUID RETAINED = UUID.fromString("dddddddd-0000-4000-8000-000000000002");
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final Map<UUID, DataSource> SOURCES;

    static {
        DB.start();
        var admin = new JdbcTemplate(source(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
        for (String suffix : new String[] {"a", "b"}) {
            admin.execute("CREATE ROLE users_" + suffix + " LOGIN PASSWORD 'test-only'");
            admin.execute("CREATE DATABASE users_" + suffix);
        }
        SOURCES = Map.of(A, runtime("a"), B, runtime("b"));
        for (UUID workspace : new UUID[] {A, B}) {
            var owner = owner(workspace);
            new ResourceDatabasePopulator(new ClassPathResource("db/changelog/changes/006-identity-app-user.sql"))
                    .execute(java.util.Objects.requireNonNull(owner.getDataSource()));
            owner.execute(
                    "CREATE TABLE tenant_identity(singleton boolean PRIMARY KEY CHECK(singleton), tenant_id uuid NOT NULL)");
            owner.update("INSERT INTO tenant_identity VALUES(true,?)", workspace);
            owner.execute("CREATE FUNCTION tenant_guard_ok() RETURNS boolean LANGUAGE sql STABLE AS $$"
                    + " SELECT NULLIF(current_setting('app.tenant_id', true),'')::uuid ="
                    + " (SELECT tenant_id FROM tenant_identity WHERE singleton) $$");
            owner.execute("CREATE TABLE department(id uuid PRIMARY KEY,name text NOT NULL,status text NOT NULL)");
            owner.execute("ALTER TABLE app_user ADD COLUMN department_id uuid REFERENCES department(id)");
            owner.execute(
                    "CREATE TABLE user_role(user_id uuid REFERENCES app_user,role text NOT NULL,PRIMARY KEY(user_id,role))");
            for (String table : new String[] {"department", "app_user", "user_role"}) {
                owner.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
                owner.execute("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
                owner.execute("CREATE POLICY tenant_guard ON " + table
                        + " FOR ALL USING(tenant_guard_ok()) WITH CHECK(tenant_guard_ok())");
            }
            owner.execute("GRANT USAGE ON SCHEMA public TO users_" + suffix(workspace));
            owner.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO users_" + suffix(workspace));
        }
    }

    private UserListTestDatabase() {}

    public static UUID id(int number) {
        return UUID.fromString("bbbbbbbb-0000-4000-8000-" + String.format("%012d", number));
    }

    public static JdbcTemplate owner(UUID workspace) {
        return new JdbcTemplate(source(url(suffix(workspace)), DB.getUsername(), DB.getPassword()));
    }

    public static void seed() {
        for (UUID workspace : new UUID[] {A, B}) {
            var jdbc = owner(workspace);
            jdbc.execute("TRUNCATE app_user,department CASCADE");
            jdbc.update("UPDATE tenant_identity SET tenant_id=?", workspace);
            jdbc.update(
                    "INSERT INTO department VALUES(?,'IT','ACTIVE'),(?,'Retained','INACTIVE')", DEPARTMENT, RETAINED);
            jdbc.update(
                    "INSERT INTO app_user(id,department_id,full_name,email,status,created_at) VALUES"
                            + "(?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP),(?,NULL,?,?,'ACTIVE',CURRENT_TIMESTAMP)",
                    ADMIN,
                    DEPARTMENT,
                    suffix(workspace) + " Admin",
                    suffix(workspace) + "-admin@example.test",
                    EMPLOYEE,
                    suffix(workspace) + " Employee",
                    suffix(workspace) + "-employee@example.test");
            jdbc.update("INSERT INTO user_role VALUES(?,'ADMIN')", ADMIN);
        }
        var jdbc = owner(A);
        jdbc.update(
                "INSERT INTO app_user(id,department_id,full_name,email,status,created_at) VALUES"
                        + "(?,?,'Resolver Manager','resolver@example.test','ACTIVE',CURRENT_TIMESTAMP),"
                        + "(?,?,'Deactivated','deactivated@example.test','DEACTIVATED',CURRENT_TIMESTAMP),"
                        + "(?,NULL,'Literal %_','literal@example.test','ACTIVE',CURRENT_TIMESTAMP)",
                id(3), DEPARTMENT, id(4), RETAINED, id(5));
        jdbc.update("INSERT INTO user_role VALUES(?,'TICKET_RESOLVER'),(?,'PROJECT_MANAGER')", id(3), id(3));
    }

    public static DataSource routing(TestTenantContext context) {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                return SOURCES.get(context.requireWorkspaceId()).getConnection();
            }

            @Override
            public Connection getConnection(String username, String password) throws java.sql.SQLException {
                throw new java.sql.SQLException("Explicit credentials are unsupported by test tenant adapter");
            }
        };
    }

    public static final class TestTenantContext implements TenantContext {
        private final ThreadLocal<UUID> current = new ThreadLocal<>();

        @Override
        public Optional<UUID> currentWorkspaceId() {
            return Optional.ofNullable(current.get());
        }

        @Override
        public UUID requireWorkspaceId() {
            return currentWorkspaceId().orElseThrow(() -> new IllegalStateException("Missing test tenant"));
        }

        @Override
        public <T> T runIn(UUID workspace, Supplier<T> action) {
            UUID previous = current.get();
            if (previous != null && !previous.equals(workspace))
                throw new IllegalStateException("Cross tenant test bind");
            current.set(workspace);
            try {
                return action.get();
            } finally {
                if (previous == null) current.remove();
                else current.set(previous);
            }
        }
    }

    /** Platform sets the transaction-local tenant setting. Identity's production helper must only validate it. */
    public static final class TestTransactionManager extends JdbcTransactionManager {
        private final TestTenantContext context;
        public boolean omitSetting;
        public UUID wrongSetting;

        public TestTransactionManager(DataSource source, TestTenantContext context) {
            super(source);
            this.context = context;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            if (!omitSetting) {
                var jdbc = new JdbcTemplate(java.util.Objects.requireNonNull(getDataSource()));
                jdbc.queryForObject(
                        "SELECT set_config('app.tenant_id', ?, true)",
                        String.class,
                        (wrongSetting == null ? context.requireWorkspaceId() : wrongSetting).toString());
            }
        }
    }

    private static DataSource runtime(String suffix) {
        return source(url(suffix), "users_" + suffix, "test-only");
    }

    private static String suffix(UUID workspace) {
        if (A.equals(workspace)) return "a";
        if (B.equals(workspace)) return "b";
        throw new IllegalArgumentException("Unknown test tenant");
    }

    private static String url(String suffix) {
        return DB.getJdbcUrl().replace("/" + DB.getDatabaseName(), "/users_" + suffix);
    }

    private static DataSource source(String url, String user, String password) {
        return new DriverManagerDataSource(url, user, password);
    }
}
