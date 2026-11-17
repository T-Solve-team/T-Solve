package vn.tsolve.chatbot.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator.CreatedIssue;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator.NewIssue;
import vn.tsolve.chatbot.issue.controller.ChatbotIssueController;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueRequest;
import vn.tsolve.chatbot.issue.repository.IssueTargetRepository;
import vn.tsolve.chatbot.issue.service.ChatbotIssueService;
import vn.tsolve.chatbot.issue.service.impl.ChatbotIssueServiceImpl;
import vn.tsolve.common.exception.GlobalExceptionHandler;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.tracker.dto.JiraAccount;

class ChatbotIssueIT {
    private static final String PATH = "/api/v1/chatbot/answers/{answerId}/jira-issue";
    private static final JiraAccount ACCOUNT = new JiraAccount("https://workspace.atlassian.net/", "cloud", "token");
    private static final CreatedIssue CREATED =
            new CreatedIssue("HELP-12", "https://workspace.atlassian.net/browse/HELP-12");
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static ChatbotAnswerRepository answers;
    private JiraIssueCreator creator;
    private ChatbotIssueService service;
    private MockMvc mvc;
    private TSolveUser owner;
    private TSolveUser other;
    private UUID answerId;
    private long departmentId;

    @BeforeAll
    static void migrateDatabaseOnSharedServer() throws Exception {
        DataSource source = TwoTenantDatabases.emptyDatabase("chatbot_issue_create");
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection();
                var liquibase = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection))) {
            liquibase.update("");
        }
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("vn.tsolve.chatbot.answer.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();
        answers = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()))
                .getRepository(ChatbotAnswerRepository.class);
    }

    @AfterAll
    static void closeFactory() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void seedAndWireService() {
        jdbc.execute("TRUNCATE app_user, department CASCADE");
        owner = user("owner@example.test");
        other = user("other@example.test");
        answerId = answer("ANSWERED");
        departmentId = jdbc.queryForObject(
                "INSERT INTO department (name, status) VALUES ('Support', 'ACTIVE') RETURNING department_id",
                Long.class);
        long projectId = jdbc.queryForObject("""
                INSERT INTO project (department_id, jira_project_key, status)
                VALUES (?, 'HELP', 'ACTIVE') RETURNING project_id
                """, Long.class, departmentId);
        jdbc.update("UPDATE department SET default_project_id = ? WHERE department_id = ?", projectId, departmentId);
        link(owner, "jira-owner", "2026-11-01T00:00:00Z");
        creator = mock(JiraIssueCreator.class);
        when(creator.create(any(), any())).thenReturn(CREATED);
        var target = new ChatbotIssueServiceImpl(
                answers,
                new IssueTargetRepository(jdbc),
                mock(LlmClient.class),
                new ObjectMapper(),
                Runnable::run,
                "60s",
                () -> Optional.of(ACCOUNT),
                creator);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(
                new JpaTransactionManager(factory.getObject()), new AnnotationTransactionAttributeSource()));
        service = (ChatbotIssueService) proxy.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(new ChatbotIssueController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()));
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAndStoresKeyThenReturnsExistingIssueWithoutJira_AC_011_1_AC_011_2_AC_011_3() throws Exception {
        for (String outcome : List.of("ANSWERED", "NO_SOURCE")) {
            UUID id = outcome.equals("ANSWERED") ? answerId : answer(outcome);
            mvc.perform(post(PATH, id).contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.issueKey").value("HELP-12"))
                    .andExpect(jsonPath("$.issueUrl").value(CREATED.issueUrl()));
            assertThat(key(id)).isEqualTo("HELP-12");
            mvc.perform(post(PATH, id).contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.issueKey").value("HELP-12"));
        }
        verify(creator, times(2))
                .create(ACCOUNT, new NewIssue("HELP", "Reviewed summary", "Reviewed description", "jira-owner"));
    }

    @Test
    void serializesConcurrentCreatesWithOneJiraCall_AC_011_3() throws Exception {
        var enteredJira = new CountDownLatch(1);
        var releaseJira = new CountDownLatch(1);
        var start = new CyclicBarrier(2);
        when(creator.create(any(), any())).thenAnswer(invocation -> {
            enteredJira.countDown();
            assertThat(releaseJira.await(10, TimeUnit.SECONDS)).isTrue();
            return CREATED;
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.create(owner, answerId.toString(), request());
            });
            var second = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.create(owner, answerId.toString(), request());
            });
            try {
                assertThat(enteredJira.await(5, TimeUnit.SECONDS)).isTrue();
                awaitBlockedAnswerLookup();
                verify(creator).create(any(), any());
            } finally {
                releaseJira.countDown();
            }
            assertThat(List.of(
                            first.get(10, TimeUnit.SECONDS).created(),
                            second.get(10, TimeUnit.SECONDS).created()))
                    .containsExactlyInAnyOrder(true, false);
        }
        verify(creator).create(any(), any());
        assertThat(key(answerId)).isEqualTo("HELP-12");
    }

    @Test
    void usesLatestLinkedReporterOfOnlyTheAsker_AC_011_4() {
        link(owner, "latest-owner", "2026-11-16T00:00:00Z");
        link(other, "other-newest", "2026-11-17T00:00:00Z");
        assertThat(service.create(owner, answerId.toString(), request()).created())
                .isTrue();
        verify(creator)
                .create(ACCOUNT, new NewIssue("HELP", "Reviewed summary", "Reviewed description", "latest-owner"));
    }

    @ParameterizedTest
    @CsvSource({"MSG148,502", "MSG157,502", "MSG149,503"})
    void storesNothingWhenJiraRefuses_AC_011_6_AC_011_7_AC_011_8(String code, int status) throws Exception {
        when(creator.create(any(), any()))
                .thenThrow(new MessageCodeException(HttpStatus.valueOf(status), code, Map.of()) {});
        var before = jdbc.queryForMap("SELECT * FROM chatbot_answer WHERE chatbot_answer_id = ?", answerId);
        mvc.perform(post(PATH, answerId).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
        assertThat(jdbc.queryForMap("SELECT * FROM chatbot_answer WHERE chatbot_answer_id = ?", answerId))
                .isEqualTo(before);
        assertThat(key(answerId)).isNull();
        verify(creator).create(any(), any());
    }

    @Test
    void refusesOtherUsersAnswerWithoutRevealingAnExistingIssue_AC_011_9() {
        jdbc.update("UPDATE chatbot_answer SET jira_issue_key = 'HELP-12' WHERE chatbot_answer_id = ?", answerId);
        assertThatThrownBy(() -> service.create(other, answerId.toString(), request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("MSG04");
        assertThat(key(answerId)).isEqualTo("HELP-12");
        verifyNoInteractions(creator);
    }

    private void awaitBlockedAnswerLookup() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE '%chatbot_answer%'
                    """, Integer.class) > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("The second create did not wait for the answer row lock");
    }

    private ChatbotIssueRequest request() {
        return new ChatbotIssueRequest("Reviewed summary", "Reviewed description", departmentId);
    }

    private String body() throws Exception {
        return new ObjectMapper().writeValueAsString(request());
    }

    private String key(UUID id) {
        return jdbc.queryForObject(
                "SELECT jira_issue_key FROM chatbot_answer WHERE chatbot_answer_id = ?", String.class, id);
    }

    private TSolveUser user(String email) {
        UUID id = jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                email);
        return new TSolveUser(id, email, "User", Set.of());
    }

    private UUID answer(String outcome) {
        return jdbc.queryForObject("""
                INSERT INTO chatbot_answer (user_id, question, outcome, answer_text)
                VALUES (?, 'Stored question', ?, 'Stored answer') RETURNING chatbot_answer_id
                """, UUID.class, owner.id(), outcome);
    }

    private void link(TSolveUser user, String reporter, String linkedAt) {
        jdbc.update(
                "INSERT INTO jira_account_links (account_id, tracker_account_id, linked_at) VALUES (?, ?, ?::timestamptz)",
                user.id(),
                reporter,
                linkedAt);
    }
}
