package vn.tsolve.chatbot.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.conversation.repository.ChatMessageRepository;
import vn.tsolve.chatbot.conversation.repository.ChatSessionRepository;
import vn.tsolve.chatbot.conversation.service.ChatbotConversationService;
import vn.tsolve.chatbot.conversation.service.impl.ChatbotConversationServiceImpl;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;

abstract class ConversationDatabaseITSupport {
    protected static JdbcTemplate jdbc;
    protected static LocalContainerEntityManagerFactoryBean factory;
    protected static ChatbotAnswerRepository answers;
    protected static ChatSessionRepository sessions;
    protected static ChatMessageRepository messages;
    protected TSolveUser owner;
    protected TSolveUser other;

    @BeforeAll
    static void migrateOnSharedServer() throws Exception {
        var source = TwoTenantDatabases.emptyDatabase(
                "conversation_api_" + UUID.randomUUID().toString().replace("-", ""));
        jdbc = new JdbcTemplate(source);
        try (var liquibase = new Liquibase(
                "db/changelog/db.changelog-master.xml",
                new ClassLoaderResourceAccessor(),
                new JdbcConnection(source.getConnection()))) {
            liquibase.update("");
        }
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("vn.tsolve.chatbot.answer.entity", "vn.tsolve.chatbot.conversation.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();
        var repositories =
                new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()));
        answers = repositories.getRepository(ChatbotAnswerRepository.class);
        sessions = repositories.getRepository(ChatSessionRepository.class);
        messages = repositories.getRepository(ChatMessageRepository.class);
    }

    @AfterAll
    static void closeFactory() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void seedUsers() {
        jdbc.execute("TRUNCATE app_user CASCADE");
        owner = user("owner@example.test");
        other = user("other@example.test");
    }

    protected void run(Consumer<ChatbotConversationService> test) {
        run(answers, mock(AuditService.class), test);
    }

    protected void run(
            ChatbotAnswerRepository answerRepository, AuditService audit, Consumer<ChatbotConversationService> test) {
        new ApplicationContextRunner()
                .withUserConfiguration(Transactions.class, ChatbotConversationServiceImpl.class)
                .withBean(ChatSessionRepository.class, () -> sessions)
                .withBean(ChatMessageRepository.class, () -> messages)
                .withBean(ChatbotAnswerRepository.class, () -> answerRepository)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AuditService.class, () -> audit)
                .withBean(PlatformTransactionManager.class, () -> new JpaTransactionManager(factory.getObject()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(ChatbotConversationService.class));
                });
    }

    protected UUID session(TSolveUser user, String title, Instant started, Instant lastMessage) {
        return jdbc.queryForObject(
                """
                INSERT INTO chat_session (user_id, title, started_at, last_message_at)
                VALUES (?, ?, ?, ?) RETURNING chat_session_id
                """, UUID.class, user.id(), title, Timestamp.from(started), Timestamp.from(lastMessage));
    }

    protected UUID turn(
            UUID session, TSolveUser user, String outcome, String rating, int index, Instant at, String citations) {
        UUID answer = jdbc.queryForObject(
                """
                INSERT INTO chatbot_answer (user_id, question, outcome, answer_text, rating, rated_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING chatbot_answer_id
                """,
                UUID.class,
                user.id(),
                "Question " + index,
                outcome,
                outcome.equals("ANSWERED") ? "Answer " + index : "",
                rating,
                rating == null ? null : Timestamp.from(at));
        jdbc.update(
                """
                INSERT INTO chat_message (chat_session_id, chatbot_answer_id, question, outcome, answer_text, citations, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                session,
                answer,
                "Question " + index,
                outcome,
                outcome.equals("ANSWERED") ? "Answer " + index : "",
                citations,
                Timestamp.from(at));
        return answer;
    }

    protected int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private TSolveUser user(String email) {
        UUID id = jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                email);
        return new TSolveUser(id, email, "User", Set.of());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}
}
