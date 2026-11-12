package vn.tsolve.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.lookup.WorkspaceStatusLookup;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.workspace.crypto.TokenCipher;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException;
import vn.tsolve.workspace.site.JiraSiteLinkRefusedException.Reason;
import vn.tsolve.workspace.site.JiraSiteLinker;

@SpringBootTest(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=http://127.0.0.1:9",
            "TSOLVE_ADMIN_ORIGIN=http://admin.localhost:8081",
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
@WithTSolveUser(roles = Role.ADMIN)
class WorkspaceControllerIT {
    private static final String TOKEN = "obviously-fake-jira-token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TokenCipher cipher;

    @Autowired
    private WorkspaceStatusLookup lookup;

    @MockBean
    private TicketTrackerClient tracker;

    @MockBean
    private JiraSiteLinker linker;

    @BeforeEach
    @AfterEach
    void clean() {
        for (JdbcTemplate db : List.of(TwoTenantDatabases.tenantA(), TwoTenantDatabases.tenantB())) {
            new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource())).executeWithoutResult(tx -> {
                db.execute("ALTER TABLE audit_log_entry DISABLE TRIGGER audit_log_entry_no_update_delete");
                db.update(
                        "DELETE FROM audit_log_entry WHERE target_type = 'WORKSPACE' AND target_id IN (?, ?)",
                        TwoTenantDatabases.ACME,
                        TwoTenantDatabases.BETA);
                db.execute("ALTER TABLE audit_log_entry ENABLE TRIGGER audit_log_entry_no_update_delete");
                db.update("DELETE FROM workspace");
            });
        }
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) {
        return scope.runIn(TwoTenantDatabases.ACME, () -> {
            try {
                return mvc.perform(request);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private Map<String, String> input(String company, String site) {
        return Map.of(
                "companyName",
                company,
                "jiraSiteUrl",
                site,
                "serviceAccountEmail",
                "fake@example.test",
                "serviceAccountToken",
                TOKEN);
    }

    private MockHttpServletRequestBuilder setup(Map<String, String> input) throws Exception {
        return post("/api/v1/workspace/setup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input));
    }

    @Test
    @DisplayName("AC-001.1 returns 201 and stores encrypted setup only in the bound tenant")
    void createsWorkspace() throws Exception {
        String body = perform(setup(input("Fake company", " HTTPS://FAKE.ATLASSIAN.NET/ ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Fake company"))
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(mapper.readTree(body).size()).isEqualTo(3);
        assertThat(body).doesNotContain(TOKEN, "token", "Token");
        var row = TwoTenantDatabases.tenantA().queryForMap("SELECT * FROM workspace");
        assertThat(row.get("workspace_id")).isEqualTo(TwoTenantDatabases.ACME);
        assertThat(row.get("sign_in_provider")).isEqualTo("GOOGLE");
        String encrypted = (String) row.get("service_account_api_token");
        assertThat(encrypted).doesNotContain(TOKEN);
        assertThat(cipher.decrypt(TwoTenantDatabases.ACME, encrypted)).isEqualTo(TOKEN);
        assertThat(TwoTenantDatabases.tenantB().queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({"companyName,MSG01", "jiraSiteUrl,MSG01", "serviceAccountEmail,MSG01", "serviceAccountToken,MSG01"})
    @DisplayName("AC-001.2 blank setup fields return 400 Problem Details field errors and store nothing")
    void rejectsBlankField(String field, String code) throws Exception {
        var body = new java.util.HashMap<>(input("Fake company", "https://fake.atlassian.net"));
        body.put(field, " ");
        perform(setup(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].code").value(code));
        assertEmpty();
        verifyNoInteractions(tracker, linker);
    }

    @Test
    @DisplayName("AC-001.7 Jira failures return MSG156 without storing or exposing the token")
    void rejectsJira() throws Exception {
        doThrow(new JiraRejectedException(401)).when(tracker).checkServiceAccount(any());
        String body = perform(setup(input("Fake company", "https://fake.atlassian.net")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG156"))
                .andExpect(jsonPath("$.params.tracker_name").value("Jira"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("serviceAccountToken"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain(TOKEN);
        verifyNoInteractions(linker);
        assertEmpty();
    }

    @Test
    @DisplayName("AC-001.9 refused link rolls back with MSG153 on jiraSiteUrl")
    void rejectsLink() throws Exception {
        when(linker.linkJiraSite(anyString())).thenThrow(new JiraSiteLinkRefusedException(Reason.LINKED_ELSEWHERE));
        perform(setup(input("Fake company", "https://fake.atlassian.net")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG153"))
                .andExpect(jsonPath("$.params.tracker_name").value("Jira"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("jiraSiteUrl"));
        assertEmpty();
    }

    @Test
    @DisplayName("AC-001.11 second setup returns 409 and never overwrites the site")
    void rejectsSecondSetup() throws Exception {
        perform(setup(input("First", "https://first.atlassian.net"))).andExpect(status().isCreated());
        perform(setup(input("Second", "https://second.atlassian.net")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ALREADY_SET_UP"));
        perform(get("/api/v1/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("First"))
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://first.atlassian.net"));
    }

    @Test
    @DisplayName("AC-001.11 concurrent inserts return 201 and 409 with one unchanged row and audit")
    void rejectsConcurrentInsert() throws Exception {
        var barrier = new CyclicBarrier(2);
        doAnswer(call -> {
                    barrier.await(15, TimeUnit.SECONDS);
                    return null;
                })
                .when(tracker)
                .checkServiceAccount(any());
        var context = SecurityContextHolder.getContext();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var requests = List.of(
                    setup(input("First", "https://first.atlassian.net")),
                    setup(input("Second", "https://second.atlassian.net")));
            var futures = requests.stream()
                    .map(request -> pool.submit(() -> {
                        SecurityContextHolder.setContext(context);
                        try {
                            return perform(request).andReturn().getResponse();
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    }))
                    .toList();
            var first = futures.get(0).get(30, TimeUnit.SECONDS);
            var second = futures.get(1).get(30, TimeUnit.SECONDS);
            assertThat(List.of(first.getStatus(), second.getStatus())).containsExactlyInAnyOrder(201, 409);
            var winner = first.getStatus() == 201 ? first : second;
            var loser = first.getStatus() == 409 ? first : second;
            assertThat(mapper.readTree(loser.getContentAsString()).path("code").asText())
                    .isEqualTo("WORKSPACE_ALREADY_SET_UP");
            assertThat(TwoTenantDatabases.tenantA()
                            .queryForObject("SELECT jira_site_address FROM workspace", String.class))
                    .isEqualTo(mapper.readTree(winner.getContentAsString())
                            .path("jiraSiteUrl")
                            .asText());
        }
        assertThat(TwoTenantDatabases.tenantA().queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isEqualTo(1);
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM audit_log_entry WHERE target_type = 'WORKSPACE' AND target_id = ?",
                                Integer.class,
                                TwoTenantDatabases.ACME))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-001.12 setup writes one audit entry with public details and no token")
    void auditsSetup() throws Exception {
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        var rows = TwoTenantDatabases.tenantA()
                .queryForList(
                        "SELECT action, target_type, target_id, details::text AS details FROM audit_log_entry WHERE target_type = 'WORKSPACE' AND target_id = ?",
                        TwoTenantDatabases.ACME);
        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.get("action")).isEqualTo("TRACKER_CONFIGURATION_CHANGE");
        assertThat(mapper.readTree((String) row.get("details")))
                .isEqualTo(mapper.valueToTree(Map.of(
                        "companyName",
                        "Fake company",
                        "jiraSiteUrl",
                        "https://fake.atlassian.net",
                        "serviceAccountEmail",
                        "fake@example.test")));
        assertThat(row.toString()).doesNotContain(TOKEN);
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-001.13 non-Admin setup returns 403 without side effects")
    void rejectsNonAdminSetup() throws Exception {
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isForbidden());
        assertEmpty();
        verifyNoInteractions(tracker, linker);
    }

    @Test
    @DisplayName("AC-002.1 readiness is false before setup and isolated by tenant after setup")
    void readsReadiness() throws Exception {
        assertThat(scope.runIn(TwoTenantDatabases.ACME, lookup::isReady)).isFalse();
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        assertThat(scope.runIn(TwoTenantDatabases.ACME, lookup::isReady)).isTrue();
        assertThat(scope.runIn(TwoTenantDatabases.BETA, lookup::isReady)).isFalse();
    }

    @Test
    @DisplayName("AC-002.2 GET returns 200 with exactly the three public fields")
    void readsWorkspace() throws Exception {
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        var response = perform(get("/api/v1/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAccountEmail").value("fake@example.test"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(mapper.readTree(response).size()).isEqualTo(3);
        assertThat(response).doesNotContain(TOKEN, "Token", "token");
    }

    @Test
    @DisplayName("AC-002.3 GET before setup returns 404 MSG60")
    void rejectsMissingWorkspace() throws Exception {
        perform(get("/api/v1/workspace"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG60"));
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-002.4 non-Admin GET returns 403")
    void rejectsNonAdminRead() throws Exception {
        perform(get("/api/v1/workspace")).andExpect(status().isForbidden());
    }

    private void assertEmpty() {
        assertThat(TwoTenantDatabases.tenantA().queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM audit_log_entry WHERE target_type = 'WORKSPACE' AND target_id = ?",
                                Integer.class,
                                TwoTenantDatabases.ACME))
                .isZero();
    }
}
