package vn.tsolve.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test-only: one PostgreSQL server per test JVM, handing each IT class its own database, so the class still starts
 * from a clean schema without paying for a container start.
 *
 * <p>{@link #migratedDatabase()} copies a template migrated once with the application changelog ({@code CREATE
 * DATABASE … TEMPLATE}), so Spring's Liquibase finds every changeset already run. {@link #emptyDatabase()} is for
 * tests that run or check the migrations themselves.
 *
 * <pre>{@code
 * private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();
 *
 * @DynamicPropertySource
 * static void database(DynamicPropertyRegistry registry) {
 *     DB.register(registry);
 * }
 * }</pre>
 */
public final class SharedPostgres {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String TEMPLATE = "tsolve_migrated_template";

    // started once for the JVM and stopped by Testcontainers' Ryuk. Every cached Spring context keeps its own
    // connection pool open on this one server, so it allows far more connections than PostgreSQL's default 100.
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=1000");

    private static final AtomicInteger NEXT = new AtomicInteger();
    private static boolean templateMigrated;

    static {
        SERVER.start();
    }

    private SharedPostgres() {}

    /** A new database holding the application's migrated schema and no rows. */
    public static synchronized Db migratedDatabase() {
        if (!templateMigrated) {
            execute("CREATE DATABASE " + TEMPLATE);
            migrate(TEMPLATE);
            templateMigrated = true;
        }
        String name = nextName();
        execute("CREATE DATABASE " + name + " TEMPLATE " + TEMPLATE);
        return db(name);
    }

    /** A new, empty database: no tables and no Liquibase history. */
    public static synchronized Db emptyDatabase() {
        String name = nextName();
        execute("CREATE DATABASE " + name);
        return db(name);
    }

    private static String nextName() {
        return "it_" + NEXT.incrementAndGet();
    }

    private static Db db(String name) {
        return new Db(
                name,
                url(name),
                SERVER.getHost(),
                SERVER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                SERVER.getUsername(),
                SERVER.getPassword());
    }

    private static String url(String database) {
        return "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/" + database;
    }

    private static void execute(String sql) {
        try (Connection connection =
                        DriverManager.getConnection(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("could not run: " + sql, e);
        }
    }

    // the connection must be closed before the template is copied: PostgreSQL refuses a template that is in use
    private static void migrate(String database) {
        try (Connection connection =
                DriverManager.getConnection(url(database), SERVER.getUsername(), SERVER.getPassword())) {
            Database liquibaseDatabase =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), liquibaseDatabase).update("");
        } catch (Exception e) {
            throw new IllegalStateException("could not migrate the template database", e);
        }
    }

    /** One IT class's database on the shared server. */
    public record Db(String name, String jdbcUrl, String host, int port, String username, String password) {

        /** Points Spring's DataSource (and so its Liquibase) at this database. */
        public void register(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", this::jdbcUrl);
            registry.add("spring.datasource.username", this::username);
            registry.add("spring.datasource.password", this::password);
        }
    }
}
