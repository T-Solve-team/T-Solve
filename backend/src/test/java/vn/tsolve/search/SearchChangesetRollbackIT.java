package vn.tsolve.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class SearchChangesetRollbackIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final List<String> PUBLIC_ID_TABLES = List.of("ticket", "project", "department");
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @BeforeAll
    static void startServer() {
        SERVER.start();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop();
    }

    @Test
    void rollsBackEverySearchChangesetByCount_AC_005_3() throws Exception {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        try (Connection connection = dataSource.getConnection();
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
                Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        resources,
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update("");
            int searchChangesets = (int) liquibase.getDatabaseChangeLog().getChangeSets().stream()
                    .filter(changeset -> "search".equals(changeset.getAuthor()))
                    .count();
            assertThat(searchChangesets).isPositive();
            for (String table : PUBLIC_ID_TABLES) {
                assertThat(columnExists(jdbc, table, "public_id")).as(table).isTrue();
            }

            liquibase.rollback(searchChangesets, "");

            assertNoSearchArtifacts(jdbc);

            liquibase.update("");

            for (String table : PUBLIC_ID_TABLES) {
                assertThat(columnExists(jdbc, table, "public_id"))
                        .as("public id restored on %s", table)
                        .isTrue();
            }
        }
    }

    private static void assertNoSearchArtifacts(JdbcTemplate jdbc) {
        for (String table : PUBLIC_ID_TABLES) {
            assertThat(columnExists(jdbc, table, "public_id"))
                    .as("public id removed from %s", table)
                    .isFalse();
        }
        assertThat(columnExists(jdbc, "ticket", "updated_at")).isFalse();
        for (String table : List.of("ticket_embedding", "search_log", "ticket_feedback")) {
            assertThat(jdbc.queryForObject(
                            """
                            SELECT EXISTS (SELECT 1 FROM information_schema.tables
                                           WHERE table_schema = 'public' AND table_name = ?)
                            """,
                            Boolean.class,
                            table))
                    .as("table %s removed", table)
                    .isFalse();
        }
        assertThat(jdbc.queryForObject(
                        """
                        SELECT EXISTS (SELECT 1 FROM pg_proc p
                                       JOIN pg_namespace n ON n.oid = p.pronamespace
                                       WHERE n.nspname = 'public' AND p.proname = 'search_touch_updated_at')
                        """,
                        Boolean.class))
                .as("updated_at function removed")
                .isFalse();
        assertThat(jdbc.queryForObject(
                        """
                        SELECT EXISTS (SELECT 1 FROM pg_trigger t
                                       JOIN pg_class c ON c.oid = t.tgrelid
                                       JOIN pg_namespace n ON n.oid = c.relnamespace
                                       WHERE n.nspname = 'public' AND c.relname = 'ticket'
                                         AND t.tgname = 'trg_ticket_updated_at')
                        """,
                        Boolean.class))
                .as("updated_at trigger removed")
                .isFalse();
    }

    private static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (SELECT 1 FROM information_schema.columns
                               WHERE table_schema = 'public' AND table_name = ? AND column_name = ?)
                """,
                Boolean.class,
                table,
                column));
    }
}
