package vn.tsolve.chatbot.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ChatbotRatingSchemaIT {
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    private String databaseName;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void startServer() {
        SERVER.start();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop();
    }

    @BeforeEach
    void createDatabase() throws Exception {
        databaseName = "chatbot_rating_" + UUID.randomUUID().toString().replace("-", "");
        adminJdbc().execute("CREATE DATABASE " + databaseName);
        dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(5432) + "/" + databaseName,
                SERVER.getUsername(),
                SERVER.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        try (Liquibase liquibase = liquibase()) {
            liquibase.update("");
        }
    }

    @AfterEach
    void dropDatabase() {
        adminJdbc().execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
    }

    @Test
    void addsNullableRatingColumnsWithChecks_AC_008_6() {
        assertRatingColumns();
        UUID answer = insertAnswer(insertUser());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM chatbot_answer WHERE rating IS NULL AND rated_at IS NULL", Integer.class))
                .isEqualTo(1);

        for (String rating : List.of("HELPFUL", "NOT_HELPFUL")) {
            assertThat(jdbc.update(
                            "UPDATE chatbot_answer SET rating = ?, rated_at = now() WHERE chatbot_answer_id = ?",
                            rating,
                            answer))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT rating FROM chatbot_answer WHERE chatbot_answer_id = ? AND rated_at IS NOT NULL",
                            String.class,
                            answer))
                    .isEqualTo(rating);
        }

        assertThat(jdbc.update(
                        "UPDATE chatbot_answer SET rating = NULL, rated_at = NULL WHERE chatbot_answer_id = ?", answer))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM chatbot_answer WHERE rating IS NULL AND rated_at IS NULL", Integer.class))
                .isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE chatbot_answer SET rating = 'UNKNOWN', rated_at = now() WHERE chatbot_answer_id = ?",
                        answer))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chatbot_answer_rating");
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE chatbot_answer SET rating = 'HELPFUL', rated_at = NULL WHERE chatbot_answer_id = ?",
                        answer))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chatbot_answer_rated");
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE chatbot_answer SET rating = NULL, rated_at = now() WHERE chatbot_answer_id = ?",
                        answer))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chatbot_answer_rated");
    }

    @Test
    void rollsBackTheColumns_AC_008_6() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> changesets = liquibase.getDatabaseChangeLog().getChangeSets();
            int boundary = -1;
            for (int index = 0; index < changesets.size(); index++) {
                if ("024-chatbot-answer-rating".equals(changesets.get(index).getId())) {
                    boundary = index;
                    break;
                }
            }
            assertThat(boundary).isNotNegative();
            liquibase.rollback(changesets.size() - boundary, "");

            assertThat(jdbc.queryForObject("SELECT to_regclass('public.chatbot_answer')::text", String.class))
                    .isEqualTo("chatbot_answer");
            assertThat(jdbc.queryForList(
                            """
                            SELECT column_name FROM information_schema.columns
                            WHERE table_schema = 'public' AND table_name = 'chatbot_answer'
                            """,
                            String.class))
                    .containsExactlyInAnyOrder(
                            "chatbot_answer_id", "user_id", "question", "outcome", "answer_text", "created_at");
            assertThat(jdbc.queryForObject(
                            """
                            SELECT count(*) FROM pg_constraint
                            WHERE conrelid = 'public.chatbot_answer'::regclass
                              AND conname IN ('ck_chatbot_answer_rating', 'ck_chatbot_answer_rated')
                            """,
                            Integer.class))
                    .isZero();

            liquibase.update("");
            assertRatingColumns();
        }
    }

    private void assertRatingColumns() {
        for (String column : List.of("rating", "rated_at")) {
            assertThat(jdbc.queryForObject(
                            """
                            SELECT data_type FROM information_schema.columns
                            WHERE table_schema = 'public' AND table_name = 'chatbot_answer' AND column_name = ?
                            """,
                            String.class,
                            column))
                    .isEqualTo("rating".equals(column) ? "character varying" : "timestamp with time zone");
            assertThat(jdbc.queryForObject(
                            """
                            SELECT is_nullable FROM information_schema.columns
                            WHERE table_schema = 'public' AND table_name = 'chatbot_answer' AND column_name = ?
                            """,
                            String.class,
                            column))
                    .isEqualTo("YES");
        }
        assertThat(jdbc.queryForObject(
                        """
                        SELECT character_maximum_length FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'chatbot_answer' AND column_name = 'rating'
                        """,
                        Integer.class))
                .isEqualTo(20);
    }

    private UUID insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                "asker@example.test");
    }

    private UUID insertAnswer(UUID user) {
        return jdbc.queryForObject(
                """
                INSERT INTO chatbot_answer(user_id, question, outcome, answer_text)
                VALUES (?, 'Question', 'ANSWERED', 'Answer') RETURNING chatbot_answer_id
                """,
                UUID.class,
                user);
    }

    private Liquibase liquibase() throws Exception {
        Connection connection = dataSource.getConnection();
        return new Liquibase(
                CHANGELOG,
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
    }

    private static JdbcTemplate adminJdbc() {
        return new JdbcTemplate(
                new DriverManagerDataSource(SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword()));
    }
}
