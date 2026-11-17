package vn.tsolve.chatbot.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.rating.dto.ChatbotRatingRequest;
import vn.tsolve.chatbot.rating.entity.ChatbotRating;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.chatbot.rating.exception.InvalidChatbotRatingException;
import vn.tsolve.chatbot.rating.service.ChatbotRatingService;
import vn.tsolve.chatbot.rating.service.impl.ChatbotRatingServiceImpl;
import vn.tsolve.common.security.TSolveUser;

class ChatbotRatingIT {
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final Instant RATED_AT = Instant.parse("2026-11-09T05:00:00Z");
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    private String databaseName;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private ChatbotAnswerRepository repository;
    private LocalContainerEntityManagerFactoryBean factory;
    private TransactionTemplate transactions;
    private ChatbotRatingService ratings;
    private TSolveUser userA;
    private TSolveUser userB;
    private UUID answerId;
    private UUID noSourceId;

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
        databaseName = "rating_" + UUID.randomUUID().toString().replace("-", "");
        adminJdbc().execute("CREATE DATABASE " + databaseName);
        dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(5432) + "/" + databaseName,
                SERVER.getUsername(),
                SERVER.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        try (Liquibase liquibase = liquibase()) {
            liquibase.update("");
        }
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("vn.tsolve.chatbot.answer.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        repository = new JpaRepositoryFactory(entityManager).getRepository(ChatbotAnswerRepository.class);
        transactions = new TransactionTemplate(new JpaTransactionManager(factory.getObject()));
        ratings = new ChatbotRatingServiceImpl(repository, Clock.fixed(RATED_AT, ZoneOffset.UTC));
        userA = seedUser("owner@example.test");
        userB = seedUser("other@example.test");
        answerId = seedAnswer(userA, "ANSWERED");
        noSourceId = seedAnswer(userA, "NO_SOURCE");
    }

    @AfterEach
    void dropDatabase() {
        if (factory != null) {
            factory.destroy();
        }
        adminJdbc().execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
    }

    @Test
    void replacesRatingOnOwnAnswerOnly_AC_008_1() {
        Map<String, Object> noSourceBefore = row(noSourceId);
        rate(userA, answerId.toString(), new ChatbotRatingRequest(ChatbotRating.HELPFUL));
        assertThat(row(answerId))
                .containsEntry("rating", "HELPFUL")
                .containsEntry("rated_at", Timestamp.from(RATED_AT));
        assertThat(row(noSourceId)).isEqualTo(noSourceBefore);

        rate(userA, answerId.toString(), new ChatbotRatingRequest(ChatbotRating.NOT_HELPFUL));
        assertThat(row(answerId))
                .containsEntry("rating", "NOT_HELPFUL")
                .containsEntry("rated_at", Timestamp.from(RATED_AT));
        rate(userA, answerId.toString(), new ChatbotRatingRequest(ChatbotRating.NOT_HELPFUL));
        Map<String, Object> answered = row(answerId);

        rate(userA, noSourceId.toString(), new ChatbotRatingRequest(ChatbotRating.HELPFUL));
        assertThat(row(noSourceId))
                .containsEntry("rating", "HELPFUL")
                .containsEntry("rated_at", Timestamp.from(RATED_AT));
        assertThat(row(answerId)).isEqualTo(answered);
    }

    @Test
    void clearsRatingAndTime_AC_008_2() {
        rate(userA, answerId.toString(), new ChatbotRatingRequest(ChatbotRating.HELPFUL));
        remove(userA, answerId.toString());
        assertThat(row(answerId)).containsEntry("rating", null).containsEntry("rated_at", null);
        remove(userA, answerId.toString());
        assertThat(row(answerId)).containsEntry("rating", null).containsEntry("rated_at", null);
    }

    @Test
    void leavesOthersAnswerUnchanged_AC_008_3() {
        var helpful = new ChatbotRatingRequest(ChatbotRating.HELPFUL);
        var notHelpful = new ChatbotRatingRequest(ChatbotRating.NOT_HELPFUL);
        rate(userA, answerId.toString(), helpful);
        Map<String, Object> before = row(answerId);
        Map<String, Object> noSourceBefore = row(noSourceId);

        assertThatThrownBy(() -> rate(userB, answerId.toString(), notHelpful))
                .isInstanceOf(ChatbotAnswerNotFoundException.class)
                .hasMessage("MSG04");
        assertThat(row(answerId)).isEqualTo(before);
        assertThatThrownBy(() -> remove(userB, answerId.toString()))
                .isInstanceOf(ChatbotAnswerNotFoundException.class)
                .hasMessage("MSG04");
        assertThat(row(answerId)).isEqualTo(before);

        for (String unknown : new String[] {UUID.randomUUID().toString(), "not-a-uuid"}) {
            assertThatThrownBy(() -> rate(userA, unknown, helpful))
                    .isInstanceOf(ChatbotAnswerNotFoundException.class)
                    .hasMessage("MSG04");
            assertThatThrownBy(() -> remove(userA, unknown))
                    .isInstanceOf(ChatbotAnswerNotFoundException.class)
                    .hasMessage("MSG04");
        }
        assertThatThrownBy(() -> rate(userA, answerId.toString(), new ChatbotRatingRequest(null)))
                .isInstanceOf(InvalidChatbotRatingException.class)
                .hasMessage("MSG06");
        assertThatThrownBy(() -> rate(userA, answerId.toString(), null))
                .isInstanceOf(InvalidChatbotRatingException.class)
                .hasMessage("MSG06");
        assertThat(row(answerId)).isEqualTo(before);
        assertThat(row(noSourceId)).isEqualTo(noSourceBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot_answer", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void keepsAJiraQuestionWithoutAUserAndRatesItOnlyForItsProject_AC_012_3_AC_012_5_AC_012_7() {
        UUID project = UUID.randomUUID();
        UUID jiraAnswer = jdbc.queryForObject(
                "INSERT INTO chatbot_answer (source, project_id, question, outcome, answer_text) "
                        + "VALUES ('JIRA_APP', ?, 'question', 'NO_SOURCE', '') RETURNING chatbot_answer_id",
                UUID.class,
                project);
        assertThat(row(answerId)).containsEntry("source", "WEB").containsEntry("project_id", null);

        // AC-012.3: the web rating cannot reach it.
        assertThatThrownBy(() -> rate(userA, jiraAnswer.toString(), new ChatbotRatingRequest(ChatbotRating.HELPFUL)))
                .isInstanceOf(ChatbotAnswerNotFoundException.class);

        // AC-012.7: only its own Project rates it, and never a web answer.
        assertThat(rateInProject(jiraAnswer, UUID.randomUUID(), ChatbotRating.HELPFUL))
                .isZero();
        assertThat(rateInProject(answerId, project, ChatbotRating.HELPFUL)).isZero();
        assertThat(rateInProject(jiraAnswer, project, ChatbotRating.NOT_HELPFUL))
                .isEqualTo(1);
        assertThat(row(jiraAnswer)).containsEntry("rating", "NOT_HELPFUL").containsEntry("user_id", null);

        // AC-012.5: a row is either a web answer with a user or a Jira answer with a Project.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO chatbot_answer (source, question, outcome, answer_text) "
                        + "VALUES ('JIRA_APP', 'q', 'NO_SOURCE', '')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO chatbot_answer (user_id, project_id, question, outcome, answer_text) "
                                + "VALUES (?, ?, 'q', 'NO_SOURCE', '')",
                        userA.id(),
                        project))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void ratesNoGreetingOrRefusedAnswer_AC_003_1_BUG_324() {
        UUID project = UUID.randomUUID();
        for (String outcome : new String[] {"GREETING", "OFF_TOPIC", "INJECTION"}) {
            UUID web = seedAnswer(userA, outcome);
            assertThatThrownBy(() -> rate(userA, web.toString(), new ChatbotRatingRequest(ChatbotRating.HELPFUL)))
                    .isInstanceOf(ChatbotAnswerNotFoundException.class)
                    .hasMessage("MSG04");
            assertThat(row(web)).containsEntry("rating", null).containsEntry("rated_at", null);

            UUID jira = jdbc.queryForObject(
                    "INSERT INTO chatbot_answer (source, project_id, question, outcome, answer_text) "
                            + "VALUES ('JIRA_APP', ?, 'question', ?, '') RETURNING chatbot_answer_id",
                    UUID.class,
                    project,
                    outcome);
            assertThat(rateInProject(jira, project, ChatbotRating.HELPFUL)).isZero();
            assertThat(row(jira)).containsEntry("rating", null);
        }
    }

    private int rateInProject(UUID id, UUID project, ChatbotRating rating) {
        Integer changed = transactions.execute(status -> repository.updateProjectRating(id, project, rating, RATED_AT));
        return changed == null ? 0 : changed;
    }

    private void rate(TSolveUser user, String id, ChatbotRatingRequest request) {
        transactions.executeWithoutResult(status -> ratings.rate(user, id, request));
    }

    private void remove(TSolveUser user, String id) {
        transactions.executeWithoutResult(status -> ratings.remove(user, id));
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM chatbot_answer WHERE chatbot_answer_id = ?", id);
    }

    private TSolveUser seedUser(String email) {
        UUID id = jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                email);
        return new TSolveUser(id, email, "Test User", Set.of());
    }

    private UUID seedAnswer(TSolveUser user, String outcome) {
        return jdbc.queryForObject(
                "INSERT INTO chatbot_answer (user_id, question, outcome, answer_text) VALUES (?, ?, ?, ?) RETURNING chatbot_answer_id",
                UUID.class,
                user.id(),
                "question",
                outcome,
                outcome.equals("ANSWERED") ? "answer" : "");
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
