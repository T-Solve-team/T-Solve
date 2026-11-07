package vn.tsolve.chatbot;

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

class ChatbotSchemaIT {
    // Review changesets after Chatbot have no rollback; roll back on the isolated Search/Chatbot changelog.
    private static final String CHANGELOG = "db/changelog/search-only.xml";
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
        databaseName = "chatbot_" + UUID.randomUUID().toString().replace("-", "");
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
    void createsAnswerTableWithTenantConvention_AC_005_11() {
        UUID user = insertUser();
        UUID answered = insertAnswer(user, "ANSWERED", "Answer [1]");
        UUID gap = insertAnswer(user, "NO_SOURCE", "");
        assertThat(List.of(answered, gap)).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'chatbot_answer'",
                        String.class))
                .containsExactlyInAnyOrder(
                        "chatbot_answer_id", "user_id", "question", "outcome", "answer_text", "created_at");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'chatbot_answer' AND is_nullable = 'NO'
                        """,
                        Integer.class))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'chatbot_answer' AND column_name = 'created_at'
                          AND column_default = 'now()'
                        """,
                        String.class))
                .isEqualTo("timestamp with time zone");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT column_default FROM information_schema.columns
                        WHERE table_name = 'chatbot_answer' AND column_name = 'chatbot_answer_id' AND data_type = 'uuid'
                        """,
                        String.class))
                .isEqualTo("gen_random_uuid()");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM chatbot_answer WHERE created_at IS NOT NULL", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(() -> insertAnswer(UUID.randomUUID(), "ANSWERED", "Answer"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE chatbot_answer SET chatbot_answer_id = ? WHERE chatbot_answer_id = ?", answered, gap))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String column : List.of("user_id", "question", "outcome", "answer_text", "created_at")) {
            assertThatThrownBy(() -> jdbc.update(
                            "UPDATE chatbot_answer SET " + column + " = NULL WHERE chatbot_answer_id = ?", answered))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void refusesUnknownOutcome_AC_005_11() {
        UUID user = insertUser();
        assertThatThrownBy(() -> insertAnswer(user, "UNKNOWN", ""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chatbot_answer_outcome");
    }

    @Test
    void rollsBackTheAnswerTable_AC_005_11() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> changesets = liquibase.getDatabaseChangeLog().getChangeSets();
            int boundary = -1;
            for (int index = 0; index < changesets.size(); index++) {
                if ("013-chatbot-answer".equals(changesets.get(index).getId())) {
                    boundary = index;
                    break;
                }
            }
            assertThat(boundary).isNotNegative();
            liquibase.rollback(changesets.size() - boundary, "");
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.chatbot_answer')::text", String.class))
                    .isNull();
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.ticket_feedback')::text", String.class))
                    .isEqualTo("ticket_feedback");
            liquibase.update("");
            assertThat(insertAnswer(insertUser(), "NO_SOURCE", "")).isNotNull();
        }
    }

    private UUID insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                "asker@example.test");
    }

    private UUID insertAnswer(UUID user, String outcome, String text) {
        return jdbc.queryForObject(
                """
                INSERT INTO chatbot_answer(user_id, question, outcome, answer_text)
                VALUES (?, 'Question', ?, ?) RETURNING chatbot_answer_id
                """,
                UUID.class,
                user,
                outcome,
                text);
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
