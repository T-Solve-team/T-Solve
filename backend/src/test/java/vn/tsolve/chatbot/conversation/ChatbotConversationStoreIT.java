package vn.tsolve.chatbot.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tsolve.ai.client.ChatMessage;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.chatbot.answer.dto.ChatbotAnswerResponse.Outcome;
import vn.tsolve.chatbot.answer.dto.ChatbotAskRequest;
import vn.tsolve.chatbot.answer.dto.ChatbotTurnRequest;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.answer.service.ChatbotAnswerService;
import vn.tsolve.chatbot.answer.service.ChatbotRetrieval;
import vn.tsolve.chatbot.answer.service.impl.ChatbotAnswerServiceImpl;
import vn.tsolve.chatbot.conversation.exception.ChatbotConversationNotFoundException;
import vn.tsolve.chatbot.conversation.repository.ChatMessageRepository;
import vn.tsolve.chatbot.conversation.repository.ChatSessionRepository;
import vn.tsolve.chatbot.conversation.service.impl.ChatConversationServiceImpl;
import vn.tsolve.chatbot.source.service.WikiSources;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.search.retrieval.service.SolutionRetrieval;

/** STORY-013 on a real database: the conversation tables and the turns stored with their answers. */
class ChatbotConversationStoreIT {
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static DataSource source;
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static ChatbotAnswerRepository answers;
    private static ChatSessionRepository sessions;
    private static ChatMessageRepository messages;
    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final LlmClient llm = mock(LlmClient.class);
    private TSolveUser owner;
    private TSolveUser other;

    @BeforeAll
    static void migrateDatabaseOnSharedServer() throws Exception {
        source = TwoTenantDatabases.emptyDatabase("chatbot_conversation");
        jdbc = new JdbcTemplate(source);
        try (var liquibase = liquibase()) {
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
        when(embeddings.embed(any())).thenReturn(List.of(new float[1024]));
        when(llm.chat(any(), notNull())).thenReturn("{\"category\":\"QUESTION\",\"query\":\"\"}");
    }

    @Test
    void createsBothTablesWithTenantConventionAndRollback_AC_013_11() throws Exception {
        assertTables();
        UUID session = session(owner, "Title");
        UUID answer = answer(owner);
        message(session, answer, "ANSWERED", Instant.now());
        assertThatThrownBy(() -> message(session, answer, "ANSWERED", Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> message(session, answer(owner), "RATED", Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM chat_session WHERE chat_session_id = ?", session);
        assertThat(count("chat_message")).isZero();
        assertThat(count("chatbot_answer")).isEqualTo(2);

        var sql = new ClassPathResource("db/changelog/changes/055-chatbot-chat-session.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("--changeset chatbot:055-chatbot-chat-session");
        try {
            sql.lines()
                    .filter(line -> line.startsWith("--rollback "))
                    .map(line -> line.substring("--rollback ".length()))
                    .forEach(jdbc::execute);
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.chat_session')::text", String.class))
                    .isNull();
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.chat_message')::text", String.class))
                    .isNull();
        } finally {
            jdbc.update("DELETE FROM databasechangelog WHERE id = '055-chatbot-chat-session'");
            try (var liquibase = liquibase()) {
                liquibase.update("");
            }
        }
        assertTables();
    }

    @Test
    void startsAConversationTitledWithTheQuestionAndStoresTheTurn_AC_013_1_AC_013_3() {
        run(service -> {
            var response = service.ask(owner, new ChatbotAskRequest("  hello  ", null));

            assertThat(response.outcome()).isEqualTo(Outcome.GREETING);
            assertThat(response.chatSessionId()).isNotNull();
            assertThat(jdbc.queryForMap(
                            "SELECT user_id, title, started_at = last_message_at AS same FROM chat_session"))
                    .containsEntry("user_id", owner.id())
                    .containsEntry("title", "hello")
                    .containsEntry("same", true);
            assertThat(jdbc.queryForMap("""
                            SELECT chat_session_id, chatbot_answer_id, question, outcome, answer_text,
                                   citations::text AS citations
                            FROM chat_message
                            """))
                    .containsEntry("chat_session_id", response.chatSessionId())
                    .containsEntry("chatbot_answer_id", response.answerId())
                    .containsEntry("question", "hello")
                    .containsEntry("outcome", "GREETING")
                    .containsEntry("answer_text", "")
                    .containsEntry("citations", "[]");
        });
    }

    @Test
    void asksWithTheLastTenStoredTurnsAndMovesTheLastMessageTime_AC_013_2_AC_013_3() {
        UUID session = session(owner, "Turn 0");
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        for (int turn = 0; turn < 11; turn++) {
            message(session, answer(owner), turn == 10 ? "NO_SOURCE" : "ANSWERED", start.plusSeconds(turn), turn);
        }
        jdbc.update("UPDATE chat_session SET last_message_at = ?", Timestamp.from(start));

        run(service -> {
            var response = service.ask(owner, new ChatbotAskRequest("  follow-up  ", session.toString()));

            assertThat(response.outcome()).isEqualTo(Outcome.NO_SOURCE);
            assertThat(response.chatSessionId()).isEqualTo(session);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
            verify(llm).chat(prompt.capture(), notNull());
            String sent = prompt.getValue().toString();
            assertThat(sent).contains("Turn 1", "Turn 10", "Answer 9").doesNotContain("Turn 0", "Answer 10");
            verify(embeddings).embed(List.of("Turn 10\nfollow-up"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class))
                    .isEqualTo(12);
            assertThat(jdbc.queryForObject(
                            "SELECT last_message_at > ? FROM chat_session", Boolean.class, Timestamp.from(start)))
                    .isTrue();
        });
    }

    @Test
    void refusesAnUnknownConversationAndStoresNothing_AC_013_4() {
        UUID othersSession = session(other, "Other's");
        run(service -> {
            for (String id : List.of("not-a-uuid", UUID.randomUUID().toString(), othersSession.toString())) {
                assertThatThrownBy(() -> service.ask(owner, new ChatbotAskRequest("question", id)))
                        .isInstanceOf(ChatbotConversationNotFoundException.class)
                        .hasMessage("MSG04");
            }
            verifyNoInteractions(llm, embeddings);
            assertThat(count("chatbot_answer")).isZero();
            assertThat(count("chat_session")).isEqualTo(1);
        });
    }

    @Test
    void rollsBackTheAnswerAndTheConversationWhenTheTurnFails_AC_013_3() {
        var failing = mock(ChatMessageRepository.class, delegatesTo(messages));
        doThrow(new IllegalStateException("insert failed")).when(failing).saveAndFlush(any());
        run(failing, service -> {
            assertThatThrownBy(() -> service.ask(owner, new ChatbotAskRequest("hello", null)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(count("chatbot_answer")).isZero();
            assertThat(count("chat_session")).isZero();
        });
    }

    @Test
    void refusesAConversationDeletedDuringTheAsk_AC_013_4_BUG_374() {
        UUID session = session(owner, "Deleted");
        var deleting = mock(ChatSessionRepository.class, delegatesTo(sessions));
        doReturn(0).when(deleting).touch(any());
        run(deleting, messages, service -> {
            assertThatThrownBy(() -> service.ask(owner, new ChatbotAskRequest("hello", session.toString())))
                    .isInstanceOf(ChatbotConversationNotFoundException.class)
                    .hasMessage("MSG04");
            assertThat(count("chatbot_answer")).isZero();
            assertThat(count("chat_message")).isZero();
        });
    }

    @Test
    void givesTheDraftOnlyTheTurnsBeforeItsAnswer_AC_010_3() {
        UUID session = session(owner, "Turn 0");
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        UUID[] ids = new UUID[3];
        for (int turn = 0; turn < 3; turn++) {
            ids[turn] = answer(owner);
            message(session, ids[turn], "ANSWERED", start.plusSeconds(turn), turn);
        }
        var store = new ChatConversationServiceImpl(sessions, messages, new ObjectMapper());

        assertThat(store.turnsBefore(ids[2]))
                .containsExactly(
                        new ChatbotTurnRequest("Turn 0", "Answer 0"), new ChatbotTurnRequest("Turn 1", "Answer 1"));
        assertThat(store.turnsBefore(ids[0])).isEmpty();
        assertThat(store.turnsBefore(answer(owner))).isEmpty();
    }

    private void run(Consumer<ChatbotAnswerService> test) {
        run(messages, test);
    }

    private void run(ChatMessageRepository messageRepository, Consumer<ChatbotAnswerService> test) {
        run(sessions, messageRepository, test);
    }

    private void run(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            Consumer<ChatbotAnswerService> test) {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ChatbotRetrieval.class, ChatbotAnswerServiceImpl.class, ChatConversationServiceImpl.class)
                .withBean(EmbeddingClient.class, () -> embeddings)
                .withBean(LlmClient.class, () -> llm)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SolutionRetrieval.class, () -> mock(SolutionRetrieval.class))
                .withBean(WikiSources.class, () -> mock(WikiSources.class))
                .withBean(ChatbotAnswerRepository.class, () -> answers)
                .withBean(ChatSessionRepository.class, () -> sessionRepository)
                .withBean(ChatMessageRepository.class, () -> messageRepository)
                .withBean(PlatformTransactionManager.class, () -> new JpaTransactionManager(factory.getObject()))
                .withBean("applicationTaskExecutor", Executor.class, () -> Runnable::run)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(ChatbotAnswerService.class));
                });
    }

    private static void assertTables() {
        assertThat(jdbc.queryForList("""
                        SELECT table_name || '.' || column_name FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name IN ('chat_session', 'chat_message')
                          AND is_nullable = 'NO'
                        """, String.class))
                .containsExactlyInAnyOrder(
                        "chat_session.chat_session_id",
                        "chat_session.user_id",
                        "chat_session.title",
                        "chat_session.started_at",
                        "chat_session.last_message_at",
                        "chat_message.chat_message_id",
                        "chat_message.chat_session_id",
                        "chat_message.chatbot_answer_id",
                        "chat_message.question",
                        "chat_message.outcome",
                        "chat_message.answer_text",
                        "chat_message.citations",
                        "chat_message.created_at");
        assertThat(jdbc.queryForList("""
                        SELECT column_default FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND column_name IN ('chat_session_id', 'chat_message_id') AND column_default IS NOT NULL
                        """, String.class)).containsExactly("gen_random_uuid()", "gen_random_uuid()");
        assertThat(jdbc.queryForList(
                        "SELECT indexname FROM pg_indexes WHERE tablename IN ('chat_session', 'chat_message')",
                        String.class))
                .contains(
                        "idx_chat_session_user_started",
                        "idx_chat_session_last_message",
                        "idx_chat_message_session_created");
    }

    private static Liquibase liquibase() throws Exception {
        return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), new JdbcConnection(source.getConnection()));
    }

    private static int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static TSolveUser user(String email) {
        UUID id = jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                email);
        return new TSolveUser(id, email, "User", Set.of());
    }

    private static UUID session(TSolveUser user, String title) {
        return jdbc.queryForObject(
                "INSERT INTO chat_session (user_id, title) VALUES (?, ?) RETURNING chat_session_id",
                UUID.class,
                user.id(),
                title);
    }

    private static UUID answer(TSolveUser user) {
        return jdbc.queryForObject("""
                INSERT INTO chatbot_answer (user_id, question, outcome, answer_text)
                VALUES (?, 'q', 'ANSWERED', 'a') RETURNING chatbot_answer_id
                """, UUID.class, user.id());
    }

    private static void message(UUID session, UUID answer, String outcome, Instant at) {
        message(session, answer, outcome, at, 0);
    }

    private static void message(UUID session, UUID answer, String outcome, Instant at, int turn) {
        jdbc.update(
                """
                INSERT INTO chat_message
                    (chat_session_id, chatbot_answer_id, question, outcome, answer_text, citations, created_at)
                VALUES (?, ?, ?, ?, ?, '[]'::jsonb, ?)
                """,
                session,
                answer,
                "Turn " + turn,
                outcome,
                "ANSWERED".equals(outcome) ? "Answer " + turn : "",
                Timestamp.from(at));
    }
}
