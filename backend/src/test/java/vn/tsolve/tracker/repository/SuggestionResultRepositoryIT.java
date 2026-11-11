package vn.tsolve.tracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.testsupport.SharedPostgres;
import vn.tsolve.tracker.entity.SuggestionResult;

/**
 * What only PostgreSQL can show: the UUID keys, the JSONB write and the conditional update that guards a second run.
 */
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
class SuggestionResultRepositoryIT {

    private static final SharedPostgres.Db DB = SharedPostgres.migratedDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.register(registry);
    }

    private static final String SOURCES =
            "[{\"type\":\"TICKET\",\"id\":\"ITSUP-7\",\"title\":\"VPN reconnect loop\",\"similarity\":0.91}]";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SuggestionResultRepository repository;

    private UUID projectId;
    private SuggestionResult saved;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM suggestion_result");
        jdbc.update("UPDATE department SET default_project_id = NULL");
        jdbc.update("DELETE FROM project");
        jdbc.update("DELETE FROM department");
        long department =
                jdbc.queryForObject("INSERT INTO department (name) VALUES ('IT') RETURNING department_id", Long.class);
        projectId = jdbc.queryForObject(
                "INSERT INTO project (department_id, jira_project_key) VALUES (?, 'ITSUP') RETURNING public_id",
                UUID.class,
                department);
        saved = repository.saveAndFlush(
                new SuggestionResult(projectId, "ITSUP-1", "VPN drops every hour", "Reconnect fails.", Instant.now()));
    }

    @Test
    void storesTheIssueTextUnderAUuidKeyTheJobFindsItBy_AC_011_1() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT suggestion_id, project_id, issue_summary, issue_description
                  FROM suggestion_result WHERE jira_issue_key = 'ITSUP-1'
                """);

        assertThat(saved.getId()).isNotNull();
        assertThat(row.get("suggestion_id")).isEqualTo(saved.getId());
        assertThat(row.get("project_id")).isEqualTo(projectId);
        assertThat(row.get("issue_summary")).isEqualTo("VPN drops every hour");
        assertThat(row.get("issue_description")).isEqualTo("Reconnect fails.");
        assertThat(repository.findById(saved.getId()))
                .map(SuggestionResult::getJiraIssueKey)
                .contains("ITSUP-1");
    }

    @Test
    void insertsANewResultWithNoSourcesAndNoSuggestion_AC_011_5() {
        Map<String, Object> row = row();

        assertThat(row.get("sources")).isEqualTo("[]");
        assertThat(row.get("suggested_solution")).isNull();
    }

    @Test
    void storesTheSourcesAsJsonAndTheSuggestedSolution_AC_011_4() {
        int changed = repository.fillSuggestion("ITSUP-1", SOURCES, "Update the VPN client to 5.2.");

        Map<String, Object> row = row();
        assertThat(changed).isEqualTo(1);
        assertThat(row.get("first_type")).isEqualTo("TICKET");
        assertThat(row.get("suggested_solution")).isEqualTo("Update the VPN client to 5.2.");
        assertThat(row.get("reporter_answer")).isNull();
    }

    @Test
    void neverOverwritesAStoredSuggestion_AC_011_7() {
        repository.fillSuggestion("ITSUP-1", SOURCES, "Update the VPN client to 5.2.");

        int changed = repository.fillSuggestion("ITSUP-1", "[]", "Something else.");

        assertThat(changed).isZero();
        assertThat(row().get("suggested_solution")).isEqualTo("Update the VPN client to 5.2.");
    }

    @Test
    void changesNothingForAnIssueWithNoResult_AC_011_7() {
        assertThat(repository.fillSuggestion("ITSUP-404", SOURCES, "Update the VPN client to 5.2."))
                .isZero();
    }

    @Test
    void recordsTheCommentOnceAndNeverReplacesIt_AC_012_3_AC_012_4() {
        Instant first = Instant.parse("2026-11-07T10:00:00Z");

        int changed = repository.markCommented(saved.getId(), "10042", first);
        int again = repository.markCommented(saved.getId(), "10099", Instant.parse("2026-11-07T11:00:00Z"));

        assertThat(changed).isEqualTo(1);
        assertThat(again).isZero();
        assertThat(repository.findById(saved.getId())).hasValueSatisfying(result -> {
            assertThat(result.hasComment()).isTrue();
            assertThat(result.getJiraCommentId()).isEqualTo("10042");
            assertThat(result.getCommentedAt()).isEqualTo(first);
        });
    }

    @Test
    void insertsANewResultWithNoComment_AC_012_3() {
        assertThat(repository.findById(saved.getId()))
                .hasValueSatisfying(result -> assertThat(result.hasComment()).isFalse());
    }

    @Test
    void keysTheTableByUuidAndReferencesTheProjectsPublicId_AC_003_1() {
        assertThat(jdbc.queryForList("""
                        SELECT column_name || ':' || data_type FROM information_schema.columns
                         WHERE table_name = 'suggestion_result' AND column_name IN ('suggestion_id', 'project_id', 'job_ref')
                        """, String.class))
                .containsExactlyInAnyOrder("suggestion_id:uuid", "project_id:uuid");
        assertThat(jdbc.queryForObject("""
                        SELECT ccu.column_name FROM information_schema.table_constraints tc
                          JOIN information_schema.constraint_column_usage ccu USING (constraint_name)
                         WHERE tc.table_name = 'suggestion_result' AND tc.constraint_type = 'FOREIGN KEY'
                           AND ccu.table_name = 'project'
                        """, String.class)).isEqualTo("public_id");
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("""
                SELECT sources_used::text AS sources, sources_used -> 0 ->> 'type' AS first_type,
                       suggested_solution, reporter_answer
                  FROM suggestion_result WHERE jira_issue_key = 'ITSUP-1'
                """);
    }
}
