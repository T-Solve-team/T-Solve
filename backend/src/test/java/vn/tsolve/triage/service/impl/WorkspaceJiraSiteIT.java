package vn.tsolve.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.triage.service.JiraSite;

/** The Jira site comes from the bound Workspace's singleton row; none, null or blank means no issue link. */
@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://127.0.0.1:9",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
            "TSOLVE_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
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
@AutoConfigureMockMvc
@Import(TwoTenantDatabases.class)
class WorkspaceJiraSiteIT {

    @Autowired
    private JiraSite jiraSite;

    @Autowired
    private TenantScope scope;

    // the workspace table is a singleton; the other workspace ITs also leave it empty
    @BeforeEach
    @AfterEach
    void clean() {
        TwoTenantDatabases.tenantA().update("DELETE FROM workspace");
        TwoTenantDatabases.tenantB().update("DELETE FROM workspace");
    }

    @Test
    void readsTheWorkspaceJiraSite_AC_003_5() {
        workspace(TwoTenantDatabases.tenantA(), "https://acme.atlassian.net/");

        assertThat(site(TwoTenantDatabases.ACME)).contains("https://acme.atlassian.net/");
    }

    @Test
    void readsOnlyTheBoundWorkspacesSite_AC_003_5() {
        workspace(TwoTenantDatabases.tenantA(), "https://acme.atlassian.net/");
        workspace(TwoTenantDatabases.tenantB(), "https://beta.atlassian.net/");

        assertThat(site(TwoTenantDatabases.ACME)).contains("https://acme.atlassian.net/");
        assertThat(site(TwoTenantDatabases.BETA)).contains("https://beta.atlassian.net/");
    }

    @Test
    void isEmptyWithoutAWorkspaceRow_AC_003_6() {
        workspace(TwoTenantDatabases.tenantB(), "https://beta.atlassian.net/");

        assertThat(site(TwoTenantDatabases.ACME)).isEmpty();
    }

    @Test
    void isEmptyWhenTheWorkspaceHasNoSite_AC_003_6() {
        workspace(TwoTenantDatabases.tenantA(), null);
        workspace(TwoTenantDatabases.tenantB(), "https://beta.atlassian.net/");

        assertThat(site(TwoTenantDatabases.ACME)).isEmpty();
        assertThat(site(TwoTenantDatabases.BETA)).contains("https://beta.atlassian.net/");
    }

    @Test
    void isEmptyWhenTheSiteIsBlank_AC_003_6() {
        workspace(TwoTenantDatabases.tenantA(), "  ");
        workspace(TwoTenantDatabases.tenantB(), "https://beta.atlassian.net/");

        assertThat(site(TwoTenantDatabases.ACME)).isEmpty();
        assertThat(site(TwoTenantDatabases.BETA)).contains("https://beta.atlassian.net/");
    }

    private Optional<String> site(UUID workspace) {
        return scope.runIn(workspace, jiraSite::url);
    }

    private static void workspace(JdbcTemplate db, String site) {
        db.update(
                "INSERT INTO workspace (workspace_id, company_name, sign_in_provider, jira_site_address)"
                        + " VALUES (?, 'Acme', 'GOOGLE', ?)",
                UUID.randomUUID(),
                site);
    }
}
