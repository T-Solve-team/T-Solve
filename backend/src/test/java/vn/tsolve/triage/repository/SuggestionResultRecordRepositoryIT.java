package vn.tsolve.triage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.triage.entity.SuggestionResultRecord;

/** What only PostgreSQL can show for the history query: the filters, the Vietnam day bounds and the order. */
@SpringBootTest
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "TSOLVE_SIGNING_KEY=" + SignInTestFixtures.KEY,
            "GOOGLE_CLIENT_ID=test-client-id",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused"
        })
class SuggestionResultRecordRepositoryIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    private static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("jiraIssueKey"));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SuggestionResultRecordRepository repository;

    private UUID itsup;
    private UUID itnet;
    private UUID hrhelp;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM suggestion_result");
        jdbc.update("UPDATE department SET default_project_id = NULL");
        jdbc.update("DELETE FROM project");
        jdbc.update("DELETE FROM department");
        long it =
                jdbc.queryForObject("INSERT INTO department (name) VALUES ('IT') RETURNING department_id", Long.class);
        long hr =
                jdbc.queryForObject("INSERT INTO department (name) VALUES ('HR') RETURNING department_id", Long.class);
        itsup = project(it, "ITSUP");
        itnet = project(it, "ITNET");
        hrhelp = project(hr, "HRHELP");

        result(itsup, "ITSUP-1", "2026-11-01T10:00:00Z", "YES");
        result(itsup, "ITSUP-2", "2026-11-01T10:00:00Z", null);
        result(itnet, "ITNET-1", "2026-11-01T16:59:59.999Z", "NO");
        result(itnet, "ITNET-2", "2026-11-01T17:00:00Z", null);
        result(hrhelp, "HRHELP-1", "2026-11-02T10:00:00Z", "YES");
    }

    private UUID project(long department, String key) {
        return jdbc.queryForObject(
                "INSERT INTO project (department_id, jira_project_key) VALUES (?, ?) RETURNING project_id",
                UUID.class,
                department,
                key);
    }

    private void result(UUID project, String key, String createdAt, String answer) {
        jdbc.update(
                "INSERT INTO suggestion_result (project_id, jira_issue_key, sources_used, suggested_solution,"
                        + " reporter_answer, created_at) VALUES (?, ?, '[{\"type\": \"TICKET\", \"id\": 1}]'::jsonb,"
                        + " 'Check the VPN.', ?, ?)",
                project,
                key,
                answer,
                Timestamp.from(Instant.parse(createdAt)));
    }

    private List<String> keys(HistoryFilter filter) {
        return repository.search(filter, PageRequest.of(0, 20, ORDER)).getContent().stream()
                .map(SuggestionResultRecord::getJiraIssueKey)
                .toList();
    }

    @Test
    void readsOnlyTheGivenProjectsNewestFirstWithTheKeyAsTieBreaker_AC_003_1() {
        assertThat(keys(new HistoryFilter(Set.of(itsup, itnet), null, null, null)))
                .containsExactly("ITNET-2", "ITNET-1", "ITSUP-1", "ITSUP-2");
    }

    @Test
    void matchesNoAnswerAndAnswersAndKeepsTheVietnamDayBounds_AC_003_2() {
        assertThat(keys(new HistoryFilter(Set.of(itsup, itnet), "NONE", null, null)))
                .containsExactly("ITNET-2", "ITSUP-2");
        assertThat(keys(new HistoryFilter(Set.of(itsup, itnet), "NO", null, null)))
                .containsExactly("ITNET-1");
        // 2026-11-01 in Asia/Ho_Chi_Minh is [2026-10-31T17:00Z, 2026-11-01T17:00Z)
        assertThat(keys(new HistoryFilter(
                        Set.of(itnet),
                        null,
                        Instant.parse("2026-10-31T17:00:00Z"),
                        Instant.parse("2026-11-01T17:00:00Z"))))
                .containsExactly("ITNET-1");
    }

    @Test
    void findsADetailOnlyInsideTheGivenProjects_AC_004_2() {
        assertThat(repository.findByJiraIssueKeyAndProjectIdIn("HRHELP-1", Set.of(itsup, itnet)))
                .isEmpty();
        assertThat(repository.findByJiraIssueKeyAndProjectIdIn("ITSUP-1", Set.of(itsup)))
                .hasValueSatisfying(record -> {
                    assertThat(record.getSourcesUsed()).contains("TICKET");
                    assertThat(record.getReporterAnswer()).isEqualTo("YES");
                });
    }
}
