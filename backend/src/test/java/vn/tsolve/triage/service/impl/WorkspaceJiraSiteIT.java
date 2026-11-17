package vn.tsolve.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
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
import vn.tsolve.triage.service.JiraSite;

/** The Jira site comes from the bound Workspace's singleton row; none, null or blank means no issue link. */
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
class WorkspaceJiraSiteIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        DB.start();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JiraSite jiraSite;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM workspace");
    }

    @Test
    void readsTheWorkspaceJiraSite_AC_003_5() {
        workspace("https://acme.atlassian.net/");

        assertThat(jiraSite.url()).contains("https://acme.atlassian.net/");
    }

    @Test
    void isEmptyWithoutAWorkspaceRow_AC_003_6() {
        assertThat(jiraSite.url()).isEmpty();
    }

    @Test
    void isEmptyWhenTheWorkspaceHasNoSite_AC_003_6() {
        workspace(null);

        assertThat(jiraSite.url()).isEmpty();
    }

    @Test
    void isEmptyWhenTheSiteIsBlank_AC_003_6() {
        workspace("  ");

        assertThat(jiraSite.url()).isEmpty();
    }

    private void workspace(String site) {
        jdbc.update(
                "INSERT INTO workspace (workspace_id, company_name, sign_in_provider, jira_site_address)"
                        + " VALUES (?, 'Acme', 'GOOGLE', ?)",
                UUID.randomUUID(),
                site);
    }
}
