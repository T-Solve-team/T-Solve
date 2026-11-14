package vn.tsolve.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.ChatbotQuestion;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Feedback;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Gaps;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.NoResultSearch;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.Suggestions;
import vn.tsolve.analytics.dto.KnowledgeDashboardResponse.TicketFeedback;
import vn.tsolve.analytics.repository.DashboardRepository;
import vn.tsolve.analytics.service.DashboardService;
import vn.tsolve.analytics.service.impl.DashboardServiceImpl;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DashboardServiceImpl.class, DashboardRepository.class, DashboardReuseGapsIT.FixedClockConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class DashboardReuseGapsIT {
    private static final String OLD = "2026-09-01T00:00:00Z";
    private static final String START = "2026-10-31T17:00:00Z";
    private static final String BEFORE = "2026-10-31T16:59:59.999Z";
    private static final String LAST = "2026-11-03T16:59:59.999Z";
    private static final String END = "2026-11-03T17:00:00Z";

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DashboardService service;

    private UUID department;
    private UUID otherDepartment;
    private UUID project;
    private UUID siblingProject;
    private UUID otherProject;
    private UUID emptyProject;
    private UUID manager;
    private UUID otherUser;

    @BeforeEach
    void seedScope() {
        department = insertDepartment("Dashboard Alpha");
        otherDepartment = insertDepartment("Dashboard Other");
        project = insertProject(department, "APP");
        siblingProject = insertProject(department, "SIBLING");
        otherProject = insertProject(otherDepartment, "OTHER");
        emptyProject = insertProject(department, "EMPTY");
        manager = insertUser(department);
        otherUser = insertUser(otherDepartment);
    }

    @Test
    void countsLatestRatingsWithinHalfOpenPeriodAndReturnsNumericRate_AC_004_10() {
        UUID ticket = insertTicket(project, false, "Rated ticket", UUID.randomUUID());
        insertFeedback(ticket, "HELPFUL", OLD, START);
        insertFeedback(ticket, "HELPFUL", OLD, LAST);
        insertFeedback(ticket, "NOT_HELPFUL", OLD, LAST);
        insertFeedback(ticket, "NOT_HELPFUL", OLD, BEFORE);
        insertFeedback(ticket, "HELPFUL", START, END);

        var result = dashboard(Role.ADMIN, null, null).feedback();
        var rated = new TicketFeedback(publicTicketId(ticket), "Rated ticket", 2, 1, 2.0 / 3);
        assertThat(result).isEqualTo(new Feedback(2, 1, 2.0 / 3, List.of(rated), List.of()));
        assertThat(service.dashboard(user(Role.ADMIN), "2026-11-05", "2026-11-07", null, null)
                        .feedback())
                .isEqualTo(new Feedback(0, 0, null, List.of(), List.of()));
    }

    @Test
    void ranksAtMostFiveByRateThenCountTitleAndPublicIdIncludingLegacyTickets_AC_004_10() {
        var zulu = ratedTicket("Zulu", 3, 0, true, UUID.randomUUID());
        var alpha = ratedTicket("Alpha", 2, 0, false, UUID.randomUUID());
        var beta = ratedTicket("Beta", 2, 0, false, UUID.randomUUID());
        var tieFirst = ratedTicket("Tie", 2, 0, false, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var tieLast = ratedTicket("Tie", 2, 0, false, UUID.fromString("00000000-0000-0000-0000-000000000002"));
        var small = ratedTicket("Small", 1, 0, false, UUID.randomUUID());
        var medium = ratedTicket("Medium", 1, 1, false, UUID.randomUUID());
        var low = ratedTicket("Low", 0, 2, false, UUID.randomUUID());

        var result = dashboard(Role.ADMIN, null, null).feedback();
        assertThat(result.helpful()).isEqualTo(13);
        assertThat(result.notHelpful()).isEqualTo(3);
        assertThat(result.helpfulRate()).isEqualTo(13.0 / 16);
        assertThat(result.topRated()).containsExactly(zulu, alpha, beta, tieFirst, tieLast);
        assertThat(result.lowestRated()).containsExactly(low, medium, small);
    }

    @Test
    void ranksOnlyRemainingTwoOfSevenRatedTicketsAsLowest_AC_004_10() {
        var first = ratedTicket("First", 5, 0, true, UUID.randomUUID());
        var second = ratedTicket("Second", 4, 0, false, UUID.randomUUID());
        var third = ratedTicket("Third", 3, 0, false, UUID.randomUUID());
        var fourth = ratedTicket("Fourth", 2, 0, false, UUID.randomUUID());
        var fifth = ratedTicket("Fifth", 1, 0, false, UUID.randomUUID());
        var medium = ratedTicket("Medium", 1, 1, false, UUID.randomUUID());
        var low = ratedTicket("Low", 0, 2, false, UUID.randomUUID());

        var result = dashboard(Role.ADMIN, null, null).feedback();
        assertThat(result.helpful()).isEqualTo(16);
        assertThat(result.notHelpful()).isEqualTo(3);
        assertThat(result.helpfulRate()).isEqualTo(16.0 / 19);
        assertThat(result.topRated()).containsExactly(first, second, third, fourth, fifth);
        assertThat(result.lowestRated()).containsExactly(low, medium);
        assertThat(result.lowestRated())
                .extracting(TicketFeedback::ticketId)
                .doesNotContainAnyElementsOf(
                        result.topRated().stream().map(TicketFeedback::ticketId).toList());
    }

    @Test
    void returnsThreeTopTicketsAndNoLowestWhenOnlyThreeAreRated_AC_004_10() {
        var high = ratedTicket("High", 1, 0, true, UUID.randomUUID());
        var medium = ratedTicket("Medium", 1, 1, false, UUID.randomUUID());
        var low = ratedTicket("Low", 0, 1, false, UUID.randomUUID());

        var result = dashboard(Role.ADMIN, null, null).feedback();
        assertThat(result).isEqualTo(new Feedback(2, 2, 0.5, List.of(high, medium, low), List.of()));
    }

    @Test
    void scopesFeedbackToTicketDepartmentAndProjectAndReturnsNullWhenEmpty_AC_004_10() {
        UUID own = insertTicket(project, true, "Own", UUID.randomUUID());
        UUID sibling = insertTicket(siblingProject, false, "Sibling", UUID.randomUUID());
        UUID foreign = insertTicket(otherProject, false, "Foreign", UUID.randomUUID());
        insertFeedback(own, "HELPFUL", OLD, START);
        insertFeedback(sibling, "NOT_HELPFUL", OLD, START);
        insertFeedback(foreign, "NOT_HELPFUL", OLD, START);
        insertFeedback(foreign, "NOT_HELPFUL", OLD, LAST);
        assertThat(dashboard(Role.ADMIN, null, null).feedback().notHelpful()).isEqualTo(3);
        var scoped = dashboard(Role.ADMIN, department, null).feedback();
        assertThat(scoped.helpful()).isEqualTo(1);
        assertThat(scoped.notHelpful()).isEqualTo(1);
        assertThat(scoped.helpfulRate()).isEqualTo(0.5);
        assertThat(scoped.topRated()).extracting(TicketFeedback::title).containsExactly("Own", "Sibling");
        assertThat(dashboard(Role.PROJECT_MANAGER, null, null).feedback()).isEqualTo(scoped);
        var selected = dashboard(Role.ADMIN, department, project).feedback();
        assertThat(selected.helpful()).isEqualTo(1);
        assertThat(selected.notHelpful()).isZero();
        assertThat(selected.helpfulRate()).isEqualTo(1.0);
        assertThat(selected.topRated()).extracting(TicketFeedback::ticketId).containsExactly(publicTicketId(own));
        assertThat(dashboard(Role.ADMIN, null, project).feedback()).isEqualTo(selected);
        assertThat(dashboard(Role.PROJECT_MANAGER, null, project).feedback()).isEqualTo(selected);
        assertThat(dashboard(Role.ADMIN, null, emptyProject).feedback())
                .isEqualTo(new Feedback(0, 0, null, List.of(), List.of()));
    }

    @Test
    void groupsSearchVariantsUsingEarliestSpellingAcrossChannelsAndDepartmentSnapshot_AC_004_11() {
        insertSearch(otherUser, department, " vpn ERROR ", "ISSUE_PANEL", 0, LAST);
        insertSearch(manager, department, "VPN error", "WEB", 0, START);
        insertSearch(manager, department, " VpN Error ", "CHATBOT", 0, LAST);
        insertSearch(manager, department, "vpn error", "WEB", 1, START);
        insertSearch(manager, department, "vpn error", "WEB", 0, BEFORE);
        insertSearch(manager, department, "vpn error", "WEB", 0, END);
        insertSearch(manager, department, " Beta ", "WEB", 0, START);
        insertSearch(manager, department, " Alpha ", "WEB", 0, START);
        insertSearch(manager, otherDepartment, "Foreign", "WEB", 0, START);
        insertSearch(manager, null, "Unassigned", "CHATBOT", 0, START);

        var scoped = dashboard(Role.ADMIN, department, null).gaps();
        assertThat(scoped.noResultSearches())
                .containsExactly(
                        new NoResultSearch("VPN error", 3),
                        new NoResultSearch("Alpha", 1),
                        new NoResultSearch("Beta", 1));
        assertThat(dashboard(Role.PROJECT_MANAGER, null, null).gaps()).isEqualTo(scoped);
        assertThat(dashboard(Role.PROJECT_MANAGER, null, emptyProject).gaps()).isEqualTo(scoped);
        assertThat(dashboard(Role.ADMIN, department, emptyProject).gaps()).isEqualTo(scoped);
        var workspace = dashboard(Role.ADMIN, null, null).gaps();
        assertThat(workspace.noResultSearches())
                .containsExactly(
                        new NoResultSearch("VPN error", 3),
                        new NoResultSearch("Alpha", 1),
                        new NoResultSearch("Beta", 1),
                        new NoResultSearch("Foreign", 1),
                        new NoResultSearch("Unassigned", 1));
        assertThat(dashboard(Role.ADMIN, null, project).gaps()).isEqualTo(workspace);
    }

    @Test
    void countsAndGroupsOnlyNoSourceQuestionsByAskerDepartmentAndPeriod_AC_004_11() {
        insertAnswer(manager, " vpn QUESTION ", "NO_SOURCE", LAST);
        insertAnswer(manager, "VPN question", "NO_SOURCE", START);
        insertAnswer(manager, " VpN Question ", "NO_SOURCE", LAST);
        insertAnswer(manager, "vpn question", "ANSWERED", START);
        insertAnswer(manager, "vpn question", "NO_SOURCE", BEFORE);
        insertAnswer(manager, "vpn question", "NO_SOURCE", END);
        insertAnswer(manager, " Beta ", "NO_SOURCE", START);
        insertAnswer(manager, " Alpha ", "NO_SOURCE", START);
        insertAnswer(otherUser, "Foreign question", "NO_SOURCE", START);
        UUID unassigned = insertUser(null);
        insertAnswer(unassigned, "Unassigned question", "NO_SOURCE", LAST);

        var scoped = dashboard(Role.PROJECT_MANAGER, null, null).gaps();
        assertThat(scoped.chatbotNoSource()).isEqualTo(5);
        assertThat(scoped.chatbotQuestions())
                .containsExactly(
                        new ChatbotQuestion("VPN question", 3),
                        new ChatbotQuestion("Alpha", 1),
                        new ChatbotQuestion("Beta", 1));
        assertThat(dashboard(Role.ADMIN, department, null).gaps()).isEqualTo(scoped);
        assertThat(dashboard(Role.PROJECT_MANAGER, null, emptyProject).gaps()).isEqualTo(scoped);
        assertThat(dashboard(Role.ADMIN, department, emptyProject).gaps()).isEqualTo(scoped);
        var workspace = dashboard(Role.ADMIN, null, project).gaps();
        assertThat(workspace.chatbotNoSource()).isEqualTo(7);
        assertThat(workspace.chatbotQuestions())
                .containsExactly(
                        new ChatbotQuestion("VPN question", 3),
                        new ChatbotQuestion("Alpha", 1),
                        new ChatbotQuestion("Beta", 1),
                        new ChatbotQuestion("Foreign question", 1),
                        new ChatbotQuestion("Unassigned question", 1));
    }

    @Test
    void limitsBothGapListsToTenWithCountThenGroupingKeyOrder_AC_004_11() {
        for (int index = 11; index >= 0; index--) {
            String text = "Gap %02d".formatted(index);
            insertSearch(manager, department, text, "WEB", 0, START);
            insertAnswer(manager, text, "NO_SOURCE", START);
        }
        insertSearch(manager, department, " GAP 11 ", "ISSUE_PANEL", 0, LAST);
        insertAnswer(manager, " GAP 11 ", "NO_SOURCE", LAST);
        var result = dashboard(Role.PROJECT_MANAGER, null, project).gaps();
        var expected = List.of(
                "Gap 11", "Gap 00", "Gap 01", "Gap 02", "Gap 03", "Gap 04", "Gap 05", "Gap 06", "Gap 07", "Gap 08");
        assertThat(result.noResultSearches()).extracting(NoResultSearch::query).containsExactlyElementsOf(expected);
        assertThat(result.noResultSearches())
                .extracting(NoResultSearch::count)
                .containsExactly(2L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L);
        assertThat(result.chatbotQuestions())
                .extracting(ChatbotQuestion::question)
                .containsExactlyElementsOf(expected);
        assertThat(result.chatbotQuestions())
                .extracting(ChatbotQuestion::count)
                .containsExactly(2L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L);
        assertThat(result.chatbotNoSource()).isEqualTo(13);
    }

    @Test
    void returnsEmptyGapsForPeriodWithoutEvents_AC_004_11() {
        insertSearch(manager, department, "Old search", "WEB", 0, BEFORE);
        insertAnswer(manager, "Old question", "NO_SOURCE", BEFORE);
        assertThat(dashboard(Role.ADMIN, null, null).gaps()).isEqualTo(new Gaps(List.of(), 0, List.of()));
    }

    @Test
    void countsOnlyCommentedSuggestionsAndYesAnswersWithinHalfOpenPeriod_AC_004_12() {
        insertSuggestion(project, "YES", START, OLD);
        insertSuggestion(project, "YES", LAST, OLD);
        insertSuggestion(project, "NO", LAST, OLD);
        insertSuggestion(project, null, START, OLD);
        insertSuggestion(project, "YES", null, START);
        insertSuggestion(project, "YES", BEFORE, START);
        insertSuggestion(project, "YES", END, START);
        assertThat(dashboard(Role.ADMIN, null, null).suggestions()).isEqualTo(new Suggestions(4, 2, 0.5));
        assertThat(service.dashboard(user(Role.ADMIN), "2026-11-05", "2026-11-07", null, null)
                        .suggestions())
                .isEqualTo(new Suggestions(0, 0, null));
    }

    @Test
    void scopesSuggestionsByProjectPublicIdAndDepartmentAndReturnsNullWhenEmpty_AC_004_12() {
        insertSuggestion(project, "YES", START, OLD);
        insertSuggestion(project, "NO", LAST, OLD);
        insertSuggestion(project, null, LAST, OLD);
        insertSuggestion(siblingProject, "YES", LAST, OLD);
        insertSuggestion(otherProject, "YES", LAST, OLD);
        assertThat(dashboard(Role.ADMIN, null, null).suggestions()).isEqualTo(new Suggestions(5, 3, 0.6));
        assertThat(dashboard(Role.ADMIN, department, null).suggestions()).isEqualTo(new Suggestions(4, 2, 0.5));
        assertThat(dashboard(Role.PROJECT_MANAGER, null, null).suggestions()).isEqualTo(new Suggestions(4, 2, 0.5));
        var selected = new Suggestions(3, 1, 1.0 / 3);
        assertThat(dashboard(Role.ADMIN, null, project).suggestions()).isEqualTo(selected);
        assertThat(dashboard(Role.PROJECT_MANAGER, department, project).suggestions())
                .isEqualTo(selected);
        assertThat(dashboard(Role.ADMIN, null, emptyProject).suggestions()).isEqualTo(new Suggestions(0, 0, null));
    }

    @Test
    void neverLogsQueryQuestionOrTicketTitle_AC_004_15(CapturedOutput output) {
        String query = "distinctive-dashboard-query-150";
        String question = "distinctive-dashboard-question-150";
        String title = "distinctive-dashboard-title-150";
        insertSearch(manager, department, query, "WEB", 0, START);
        insertAnswer(manager, question, "NO_SOURCE", START);
        UUID ticket = insertTicket(project, false, title, UUID.randomUUID());
        insertFeedback(ticket, "HELPFUL", OLD, START);
        var result = dashboard(Role.PROJECT_MANAGER, null, project);
        assertThat(result.gaps().noResultSearches()).containsExactly(new NoResultSearch(query, 1));
        assertThat(result.gaps().chatbotQuestions()).containsExactly(new ChatbotQuestion(question, 1));
        assertThat(result.feedback().topRated())
                .extracting(TicketFeedback::title)
                .containsExactly(title);
        assertThat(output.getAll()).doesNotContain(query, question, title);
    }

    private TicketFeedback ratedTicket(String title, int helpful, int notHelpful, boolean legacy, UUID publicId) {
        UUID ticket = insertTicket(project, legacy, title, publicId);
        for (int index = 0; index < helpful; index++) {
            insertFeedback(ticket, "HELPFUL", OLD, START);
        }
        for (int index = 0; index < notHelpful; index++) {
            insertFeedback(ticket, "NOT_HELPFUL", OLD, START);
        }
        return new TicketFeedback(publicId, title, helpful, notHelpful, (double) helpful / (helpful + notHelpful));
    }

    private KnowledgeDashboardResponse dashboard(Role role, UUID selectedDepartment, UUID selectedProject) {
        return service.dashboard(
                user(role),
                "2026-11-01",
                "2026-11-03",
                selectedDepartment == null ? null : selectedDepartment.toString(),
                selectedProject == null ? null : selectedProject.toString());
    }

    private TSolveUser user(Role role) {
        return new TSolveUser(manager, "user@example.test", "Test User", Set.of(role));
    }

    private UUID insertDepartment(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into department (public_id, name, status) values (?, ?, 'ACTIVE')", id, name);
        return id;
    }

    private UUID insertProject(UUID owningDepartment, String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into project (public_id, department_id, jira_project_key, status)
                select ?, department_id, ?, 'ACTIVE' from department where public_id = ?
                """, id, key, owningDepartment);
        return id;
    }

    private UUID insertUser(UUID owningDepartment) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app_user (id, email, full_name, status, created_at, department_id)
                values (?, ?, 'Test User', 'ACTIVE', now(),
                    (select department_id from department where public_id = ?))
                """, id, id + "@example.test", owningDepartment);
        return id;
    }

    private UUID insertTicket(UUID owningProject, boolean legacy, String title, UUID publicId) {
        UUID id = UUID.randomUUID();
        UUID storedProject = legacy
                ? jdbc.queryForObject(
                        "select lpad(to_hex(project_id), 32, '0')::uuid from project where public_id = ?",
                        UUID.class,
                        owningProject)
                : owningProject;
        jdbc.update("""
                insert into ticket (ticket_id, public_id, source_ticket_id, project_id, source, status, title,
                    symptoms, root_cause, resolution_steps, scope, created_at)
                values (?, ?, ?, ?, 'SOLUTION_FORM', 'PUBLISHED', ?, 'Symptoms', 'Cause', 'Steps', 'Scope', ?)
                """, id, publicId, id.toString(), storedProject, title, timestamp(OLD));
        return id;
    }

    private UUID publicTicketId(UUID ticket) {
        return jdbc.queryForObject("select public_id from ticket where ticket_id = ?", UUID.class, ticket);
    }

    private void insertFeedback(UUID ticket, String rating, String createdAt, String updatedAt) {
        jdbc.update(
                """
                insert into ticket_feedback (ticket_feedback_id, ticket_id, user_id, rating, channel, created_at, updated_at)
                values (?, ?, ?, ?, 'WEB', ?, ?)
                """,
                UUID.randomUUID(),
                ticket,
                insertUser(otherDepartment),
                rating,
                timestamp(createdAt),
                timestamp(updatedAt));
    }

    private void insertSearch(
            UUID asker, UUID loggedDepartment, String query, String channel, int results, String createdAt) {
        jdbc.update("""
                insert into search_log (search_log_id, user_id, department_id, query, channel, result_count, is_fallback, created_at)
                values (?, ?, (select department_id from department where public_id = ?), ?, ?, ?, true, ?)
                """, UUID.randomUUID(), asker, loggedDepartment, query, channel, results, timestamp(createdAt));
    }

    private void insertAnswer(UUID asker, String question, String outcome, String createdAt) {
        jdbc.update("""
                insert into chatbot_answer (chatbot_answer_id, user_id, question, outcome, answer_text, created_at)
                values (?, ?, ?, ?, 'Test answer', ?)
                """, UUID.randomUUID(), asker, question, outcome, timestamp(createdAt));
    }

    private void insertSuggestion(UUID owningProject, String answer, String commentedAt, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into suggestion_result (suggestion_id, project_id, jira_issue_key, sources_used,
                    reporter_answer, commented_at, created_at)
                values (?, ?, ?, '[]'::jsonb, ?, ?, ?)
                """, id, owningProject, "APP-" + id, answer, timestamp(commentedAt), timestamp(createdAt));
    }

    private Timestamp timestamp(String instant) {
        return instant == null ? null : Timestamp.from(Instant.parse(instant));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-06T18:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbc) {
            return new NamedParameterJdbcTemplate(jdbc);
        }
    }
}
