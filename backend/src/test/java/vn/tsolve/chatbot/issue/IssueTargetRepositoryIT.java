package vn.tsolve.chatbot.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import vn.tsolve.ai.client.LlmClient;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftRequest;
import vn.tsolve.chatbot.issue.dto.ChatbotIssueDraftResponse;
import vn.tsolve.chatbot.issue.dto.IssueDepartment;
import vn.tsolve.chatbot.issue.repository.IssueTargetRepository;
import vn.tsolve.chatbot.issue.repository.IssueTargetRepository.IssueTarget;
import vn.tsolve.chatbot.issue.service.ChatbotIssueService;
import vn.tsolve.chatbot.issue.service.impl.ChatbotIssueServiceImpl;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.tracker.service.JiraServiceAccount;

class IssueTargetRepositoryIT {
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static ChatbotAnswerRepository answers;
    private IssueTargetRepository targets;
    private TSolveUser owner;
    private TSolveUser other;

    @BeforeAll
    static void migrateDatabaseOnSharedServer() throws Exception {
        DataSource source = TwoTenantDatabases.emptyDatabase("chatbot_issue_draft");
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
    void seedUsers() {
        jdbc.execute("TRUNCATE app_user, department CASCADE");
        targets = new IssueTargetRepository(jdbc);
        owner = user("owner@example.test");
        other = user("other@example.test");
    }

    @Test
    void offersExactlyActiveDepartmentsWithActiveDefaultProjectsByName_AC_010_2() {
        long zulu = department("Zulu", "ACTIVE", "ACTIVE", true);
        long alpha = department("Alpha", "ACTIVE", "ACTIVE", true);
        department("Inactive department", "INACTIVE", "ACTIVE", true);
        department("Inactive project", "ACTIVE", "INACTIVE", true);
        department("No default", "ACTIVE", "ACTIVE", false);
        assertThat(targets.offeredDepartments())
                .containsExactly(
                        new IssueTarget(alpha, "Alpha", "KEY" + alpha), new IssueTarget(zulu, "Zulu", "KEY" + zulu));
        jdbc.update("UPDATE project SET status = 'INACTIVE'");
        assertThat(targets.offeredDepartments()).isEmpty();
    }

    @Test
    void readsMostRecentlyLinkedJiraAccountForOnlyTheUser_AC_010_7() {
        assertThat(targets.linkedJiraAccountId(owner.id())).isEmpty();
        link(owner, "old", "2026-11-01T00:00:00Z");
        link(owner, "latest", "2026-11-16T00:00:00Z");
        link(other, "other-latest", "2026-11-17T00:00:00Z");
        assertThat(targets.linkedJiraAccountId(owner.id())).contains("latest");
        assertThat(targets.linkedJiraAccountId(other.id())).contains("other-latest");
    }

    @Test
    void scopesAnswerLookupAndLeavesAllDraftInputsUnchanged_AC_010_9() {
        link(owner, "jira-owner", "2026-11-17T00:00:00Z");
        long department = department("Support", "ACTIVE", "ACTIVE", true);
        UUID id = jdbc.queryForObject("""
                INSERT INTO chatbot_answer (user_id, question, outcome, answer_text)
                VALUES (?, 'Stored question', 'ANSWERED', 'Stored answer') RETURNING chatbot_answer_id
                """, UUID.class, owner.id());
        assertThat(answers.findByIdAndUserId(id, owner.id())).isPresent();
        assertThat(answers.findByIdAndUserId(id, other.id())).isEmpty();
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(any(), any())).thenReturn("{\"summary\":\"Help\",\"description\":\"Details\",\"department\":1}");
        var service = new ChatbotIssueServiceImpl(
                answers,
                targets,
                llm,
                new ObjectMapper(),
                Runnable::run,
                "60s",
                mock(JiraServiceAccount.class),
                mock(JiraIssueCreator.class));
        var before = snapshot();
        assertThatThrownBy(() -> service.draft(other, id.toString(), new ChatbotIssueDraftRequest(List.of())))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("MSG04");
        verifyNoInteractions(llm);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThat(service.draft(owner, id.toString(), new ChatbotIssueDraftRequest(List.of())))
                    .isEqualTo(new ChatbotIssueDraftResponse(
                            "Help", "Details", department, List.of(new IssueDepartment(department, "Support"))));
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void returnsEmptyDraftOnConfiguredTimeoutWithoutRetry_AC_010_5() {
        UUID id = jdbc.queryForObject("""
                INSERT INTO chatbot_answer (user_id, question, outcome, answer_text)
                VALUES (?, 'Question', 'NO_SOURCE', '') RETURNING chatbot_answer_id
                """, UUID.class, owner.id());
        link(owner, "jira-owner", "2026-11-17T00:00:00Z");
        long department = department("Support", "ACTIVE", "ACTIVE", true);
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger submissions = new AtomicInteger();
        // Leave the submitted future incomplete: orTimeout must supply the fallback itself.
        Executor stalled = command -> submissions.incrementAndGet();
        new ApplicationContextRunner()
                .withUserConfiguration(ChatbotIssueServiceImpl.class)
                .withPropertyValues("CHATBOT_ASK_TIMEOUT=1s")
                .withBean(ChatbotAnswerRepository.class, () -> answers)
                .withBean(IssueTargetRepository.class, () -> targets)
                .withBean(LlmClient.class, () -> llm)
                .withBean(JiraServiceAccount.class, () -> mock(JiraServiceAccount.class))
                .withBean(JiraIssueCreator.class, () -> mock(JiraIssueCreator.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("applicationTaskExecutor", Executor.class, () -> stalled)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(assertTimeoutPreemptively(
                                    Duration.ofSeconds(5), () -> context.getBean(ChatbotIssueService.class)
                                            .draft(owner, id.toString(), new ChatbotIssueDraftRequest(List.of()))))
                            .isEqualTo(new ChatbotIssueDraftResponse(
                                    "", "", null, List.of(new IssueDepartment(department, "Support"))));
                });
        assertThat(submissions).hasValue(1);
        verifyNoInteractions(llm);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        return Map.of(
                "answers",
                jdbc.queryForList("SELECT * FROM chatbot_answer"),
                "links",
                jdbc.queryForList("SELECT * FROM jira_account_links"),
                "departments",
                jdbc.queryForList("SELECT * FROM department ORDER BY department_id"),
                "projects",
                jdbc.queryForList("SELECT * FROM project ORDER BY project_id"));
    }

    private TSolveUser user(String email) {
        UUID id = jdbc.queryForObject(
                "INSERT INTO app_user (email, status, created_at) VALUES (?, 'ACTIVE', now()) RETURNING id",
                UUID.class,
                email);
        return new TSolveUser(id, email, "User", Set.of());
    }

    private void link(TSolveUser user, String trackerId, String instant) {
        jdbc.update(
                "INSERT INTO jira_account_links (account_id, tracker_account_id, linked_at) VALUES (?, ?, ?::timestamptz)",
                user.id(),
                trackerId,
                instant);
    }

    private long department(String name, String status, String projectStatus, boolean hasDefault) {
        long id = jdbc.queryForObject(
                "INSERT INTO department (name, status) VALUES (?, ?) RETURNING department_id",
                Long.class,
                name,
                status);
        long projectId = jdbc.queryForObject("""
                INSERT INTO project (department_id, jira_project_key, status)
                VALUES (?, ?, ?) RETURNING project_id
                """, Long.class, id, "KEY" + id, projectStatus);
        if (hasDefault) {
            jdbc.update("UPDATE department SET default_project_id = ? WHERE department_id = ?", projectId, id);
        }
        return id;
    }
}
