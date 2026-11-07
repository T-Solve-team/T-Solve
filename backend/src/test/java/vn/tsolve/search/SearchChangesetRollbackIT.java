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

    private static final String CHANGELOG = "db/changelog/search-only.xml";
    private static final List<String> PUBLIC_ID_TABLES = List.of("ticket", "project", "department");
    private static final List<String> SEARCH_TABLES = List.of("ticket_embedding", "search_log", "ticket_feedback");
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
            var changesets = liquibase.getDatabaseChangeLog().getChangeSets();
            int boundary = -1;
            for (int index = 0; index < changesets.size(); index++) {
                if ("008-search-public-ids".equals(changesets.get(index).getId())) {
                    boundary = index;
                    break;
                }
            }
            assertThat(boundary).isNotNegative();
            for (String table : PUBLIC_ID_TABLES) {
                assertThat(columnExists(jdbc, table, "public_id")).as(table).isEqualTo(!"project".equals(table));
            }
            for (String table : SEARCH_TABLES) {
                assertThat(columnExists(jdbc, table, table + "_id"))
                        .as("table %s exists before rollback", table)
                        .isTrue();
            }

            liquibase.rollback(changesets.size() - boundary, "");

            assertNoSearchArtifacts(jdbc);
            assertThat(columnExists(jdbc, "chatbot_answer", "chatbot_answer_id"))
                    .isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                    .as("vector extension survives rollback")
                    .isEqualTo(1);

            liquibase.update("");

            assertThat(columnExists(jdbc, "chatbot_answer", "chatbot_answer_id"))
                    .isTrue();
            for (String table : SEARCH_TABLES) {
                assertThat(columnExists(jdbc, table, table + "_id"))
                        .as("table %s restored", table)
                        .isTrue();
            }
            for (String table : PUBLIC_ID_TABLES) {
                assertThat(columnExists(jdbc, table, "public_id"))
                        .as("public id restored on %s", table)
                        .isEqualTo(!"project".equals(table));
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
        for (String table : SEARCH_TABLES) {
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
