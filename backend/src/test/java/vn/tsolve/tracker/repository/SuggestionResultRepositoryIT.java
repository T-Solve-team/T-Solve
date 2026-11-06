package vn.tsolve.tracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.tracker.entity.SuggestionResult;

/** What only PostgreSQL can show: the JSONB write and the conditional update that guards a second run. */
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

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    private static final String SOURCES =
            "[{\"type\":\"TICKET\",\"id\":\"ITSUP-7\",\"title\":\"VPN reconnect loop\",\"similarity\":0.91}]";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SuggestionResultRepository repository;

    private long projectId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM suggestion_result");
        jdbc.update("UPDATE department SET default_project_id = NULL");
        jdbc.update("DELETE FROM project");
        jdbc.update("DELETE FROM department");
        long department =
                jdbc.queryForObject("INSERT INTO department (name) VALUES ('IT') RETURNING department_id", Long.class);
        projectId = jdbc.queryForObject(
                "INSERT INTO project (department_id, jira_project_key) VALUES (?, 'ITSUP') RETURNING project_id",
                Long.class,
                department);
        repository.saveAndFlush(new SuggestionResult(projectId, "ITSUP-1", Instant.now()));
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

    private Map<String, Object> row() {
        return jdbc.queryForMap(
                """
                SELECT sources_used::text AS sources, sources_used -> 0 ->> 'type' AS first_type,
                       suggested_solution, reporter_answer
                  FROM suggestion_result WHERE jira_issue_key = 'ITSUP-1'
                """);
    }
}
