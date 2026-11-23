package vn.tsolve.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL server per JVM that runs the REAL {@code infra/postgres/init/10-tsolve-dbs.sh} on its first start, so
 * the business, Keycloak, Jobs and Registry databases and users exist exactly as on a fresh staging volume. The
 * Registry user owns the Registry database. Testcontainers (Ryuk) stops it when the JVM exits.
 */
public final class PostgresTestBase {

    public static final String SUPERUSER = "postgres";
    public static final String SUPERUSER_PASSWORD = "fake-superuser-password";
    public static final String APP_DB = "tsolve_test_app";
    public static final String APP_USER = "tsolve_test_app_user";
    public static final String APP_PASSWORD = "fake-app-password";
    public static final String JOBS_DB = "tsolve_test_jobs";
    public static final String JOBS_USER = "tsolve_test_jobs_user";
    public static final String JOBS_PASSWORD = "fake-jobs-password";
    public static final String REGISTRY_DB = "tsolve_test_registry";
    public static final String REGISTRY_USER = "tsolve_test_registry_user";
    public static final String REGISTRY_PASSWORD = "fake-registry-password";

    private static final AtomicInteger EMPTY_COUNTER = new AtomicInteger();

    private PostgresTestBase() {}

    public static PostgreSQLContainer<?> instance() {
        return Holder.DB;
    }

    /** JDBC URL of one database on the shared server. */
    public static String url(String database) {
        return "jdbc:postgresql://" + instance().getHost() + ":" + instance().getMappedPort(5432) + "/" + database;
    }

    public static String registryUrl() {
        return url(REGISTRY_DB);
    }

    public static Connection superuserConnection(String database) throws SQLException {
        return DriverManager.getConnection(url(database), SUPERUSER, SUPERUSER_PASSWORD);
    }

    /** A new database with no changelog applied, owned by the Registry user. Returns its JDBC URL. */
    public static String emptyDatabase(String prefix) {
        String name = "empty_" + prefix + "_" + EMPTY_COUNTER.incrementAndGet();
        try (Connection connection = superuserConnection("postgres");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + name + "\" OWNER \"" + REGISTRY_USER + "\"");
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create an empty Registry database: " + exception.getMessage());
        }
        return url(name);
    }

    private static final class Holder {
        private static final PostgreSQLContainer<?> DB = start();

        private static PostgreSQLContainer<?> start() {
            // Relative to backend/, the working directory of Maven.
            var database = new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                    .withUsername(SUPERUSER)
                    .withPassword(SUPERUSER_PASSWORD)
                    .withDatabaseName("postgres")
                    .withEnv("DB_NAME", APP_DB)
                    .withEnv("DB_USER", APP_USER)
                    .withEnv("DB_PASSWORD", APP_PASSWORD)
                    .withEnv("KEYCLOAK_DB_PASSWORD", "fake-keycloak-password")
                    .withEnv("JOBS_DB_NAME", JOBS_DB)
                    .withEnv("JOBS_DB_USER", JOBS_USER)
                    .withEnv("JOBS_DB_PASSWORD", JOBS_PASSWORD)
                    .withEnv("REGISTRY_DB_NAME", REGISTRY_DB)
                    .withEnv("REGISTRY_DB_USER", REGISTRY_USER)
                    .withEnv("REGISTRY_DB_PASSWORD", REGISTRY_PASSWORD)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../infra/postgres/init/10-tsolve-dbs.sh", 0755),
                            "/docker-entrypoint-initdb.d/10-tsolve-dbs.sh");
            database.start();
            return database;
        }
    }
}
