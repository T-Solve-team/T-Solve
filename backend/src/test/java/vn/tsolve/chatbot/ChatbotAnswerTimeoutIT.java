package vn.tsolve.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.chatbot.answer.dto.ChatbotQuestionRequest;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.chatbot.answer.service.impl.ChatbotAnswerServiceImpl;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

class ChatbotAnswerTimeoutIT {
    private static final String CHANGELOG = "db/changelog/search-only.xml";
    private static final String ASK_TIMEOUT = "3s";
    private static final long SLOW_SAVE_DELAY_MILLIS = 3500;
    private static final PostgreSQLContainer<?> SERVER = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    private String databaseName;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private LocalContainerEntityManagerFactoryBean factory;

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
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("vn.tsolve.chatbot.answer.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();
    }

    @AfterEach
    void dropDatabase() {
        if (factory != null) {
            factory.destroy();
        }
        adminJdbc().execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
    }

    @Test
    void rollsBackTheAnswerRowOnTimeout_BUG_165() {
        UUID userId = jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                "asker@example.test");
        var user = new TSolveUser(userId, "asker@example.test", "Asker", Set.of());
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        var repository = new JpaRepositoryFactory(entityManager).getRepository(ChatbotAnswerRepository.class);
        var slowAnswers = mock(ChatbotAnswerRepository.class, delegatesTo(repository));
        doAnswer(invocation -> {
                    ChatbotAnswer saved = repository.saveAndFlush(invocation.getArgument(0));
                    // Prove an insert reached PostgreSQL so the timeout must roll back a real row.
                    assertThat(entityManager
                                    .createQuery("SELECT count(a) FROM ChatbotAnswer a", Long.class)
                                    .getSingleResult())
                            .isEqualTo(1L);
                    TimeUnit.MILLISECONDS.sleep(SLOW_SAVE_DELAY_MILLIS);
                    return saved;
                })
                .when(slowAnswers)
                .save(any(ChatbotAnswer.class));
        var embeddings = mock(EmbeddingClient.class);
        when(embeddings.embed(any())).thenReturn(List.of(new float[1024]));

        new ApplicationContextRunner()
                .withUserConfiguration(ChatbotAnswerServiceImpl.class)
                .withPropertyValues("CHATBOT_ASK_TIMEOUT=" + ASK_TIMEOUT)
                .withBean(EmbeddingClient.class, () -> embeddings)
                .withBean(LlmClient.class, () -> mock(LlmClient.class))
                .withBean(SolutionRetrieval.class, () -> mock(SolutionRetrieval.class))
                .withBean(WikiSources.class, () -> mock(WikiSources.class))
                .withBean(ChatbotAnswerRepository.class, () -> slowAnswers)
                .withBean(PlatformTransactionManager.class, () -> new JpaTransactionManager(factory.getObject()))
                .withBean("applicationTaskExecutor", Executor.class, () -> Runnable::run)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var service = context.getBean(ChatbotAnswerService.class);
                    assertThatThrownBy(() -> service.ask(user, new ChatbotQuestionRequest("question", List.of())))
                            .isInstanceOf(AiUnavailableException.class)
                            .hasMessage("MSG06")
                            .hasNoCause();
                    verify(slowAnswers).save(any(ChatbotAnswer.class));
                    // A fresh JDBC connection observes the database after the service transaction has ended.
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM chatbot_answer", Integer.class))
                            .isZero();
                });
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
