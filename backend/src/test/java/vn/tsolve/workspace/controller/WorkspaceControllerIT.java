package vn.tsolve.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.common.session.lookup.WorkspaceStatusLookup;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.workspace.dto.JiraAppToken;
import vn.tsolve.workspace.service.JiraAppTokenService;
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
    private static final String CLOUD_ID = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final String SITE = "https://fake.atlassian.net";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantScope scope;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private WorkspaceStatusLookup lookup;

    // The app token lives in Redis (STORY-012); this IT is about the tenant database, so the store is mocked.
    @MockBean
    private JiraAppTokenService tokens;

    @MockBean
    private JiraSiteLinker linker;

    @BeforeEach
    void readCloudId() {
        when(linker.readCloudId(anyString())).thenReturn(CLOUD_ID);
        when(tokens.current(TwoTenantDatabases.ACME)).thenReturn(Optional.empty());
    }

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
        return Map.of("companyName", company, "jiraSiteUrl", site);
    }

    private MockHttpServletRequestBuilder setup(Map<String, String> input) throws Exception {
        return post("/api/v1/workspace/setup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input));
    }

    @Test
    @DisplayName(
            "AC-005.1 returns 201 and stores the site and its cloud id, no Jira credential, only in the bound tenant")
    void createsWorkspace() throws Exception {
        String body = perform(setup(input("Fake company", " HTTPS://FAKE.ATLASSIAN.NET/ ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Fake company"))
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(mapper.readTree(body).path("jiraAppConnected").asBoolean(true))
                .isFalse();
        assertThat(body).doesNotContain(TOKEN, CLOUD_ID);
        var row = TwoTenantDatabases.tenantA().queryForMap("SELECT * FROM workspace");
        assertThat(row.get("workspace_id")).isEqualTo(TwoTenantDatabases.ACME);
        assertThat(row.get("jira_cloud_id")).isEqualTo(CLOUD_ID);
        assertThat(row.get("service_account_email")).isNull();
        assertThat(row.get("service_account_api_token")).isNull();
        assertThat(row.get("sign_in_provider")).isEqualTo("GOOGLE");
        assertThat(TwoTenantDatabases.tenantB().queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({"companyName,MSG01", "jiraSiteUrl,MSG01"})
    @DisplayName("AC-005.2 blank setup fields return 400 Problem Details field errors and store nothing")
    void rejectsBlankField(String field, String code) throws Exception {
        var body = new java.util.HashMap<>(input("Fake company", "https://fake.atlassian.net"));
        body.put(field, " ");
        perform(setup(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].code").value(code));
        assertEmpty();
        verifyNoInteractions(linker);
    }

    @Test
    @DisplayName("AC-005.14 an unreadable cloud id returns MSG61 on jiraSiteUrl without a check or a row")
    void rejectsUnreadableCloudId() throws Exception {
        when(linker.readCloudId(anyString())).thenThrow(new JiraSiteLinkRefusedException(Reason.UNREADABLE));
        perform(setup(input("Fake company", SITE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG61"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("jiraSiteUrl"));
        verify(linker, never()).linkJiraSite(anyString());
        assertEmpty();
    }

    @Test
    @DisplayName("AC-005.9 refused link rolls back with MSG153 on jiraSiteUrl")
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
    @DisplayName("AC-005.11 second setup returns 409 and never overwrites the site")
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
    @DisplayName("AC-005.11 concurrent inserts return 201 and 409 with one unchanged row and audit")
    void rejectsConcurrentInsert() throws Exception {
        var barrier = new CyclicBarrier(2);
        doAnswer(call -> {
                    barrier.await(15, TimeUnit.SECONDS);
                    return CLOUD_ID;
                })
                .when(linker)
                .linkJiraSite(anyString());
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
    @DisplayName("AC-005.12 setup writes one audit entry with the company name and the site")
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
                .isEqualTo(mapper.valueToTree(
                        Map.of("companyName", "Fake company", "jiraSiteUrl", "https://fake.atlassian.net")));
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-005.13 non-Admin setup returns 403 without side effects")
    void rejectsNonAdminSetup() throws Exception {
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isForbidden());
        assertEmpty();
        verifyNoInteractions(linker);
    }

    @Test
    @DisplayName("AC-006.1 readiness is false before setup and isolated by tenant after setup")
    void readsReadiness() throws Exception {
        assertThat(scope.runIn(TwoTenantDatabases.ACME, lookup::isCompanyInformationEntered))
                .isFalse();
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        assertThat(scope.runIn(TwoTenantDatabases.ACME, lookup::isCompanyInformationEntered))
                .isTrue();
        assertThat(scope.runIn(TwoTenantDatabases.BETA, lookup::isCompanyInformationEntered))
                .isFalse();
    }

    @Test
    @DisplayName("AC-006.1 /me reports workspaceReady=true once the company information is saved (BUG-206)")
    void meReportsWorkspaceReadyAfterSetup_BUG_206() throws Exception {
        perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceReady").value(false));
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceReady").value(true));
    }

    @Test
    @DisplayName("AC-006.2 AC-006.6 GET returns the company, the site and the Jira App status, never the token")
    void readsWorkspace() throws Exception {
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        Instant received = Instant.parse("2026-11-17T03:00:00Z");
        when(tokens.current(TwoTenantDatabases.ACME))
                .thenReturn(Optional.of(new JiraAppToken(TOKEN, CLOUD_ID, received, received.plusSeconds(3600))));
        var response = perform(get("/api/v1/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Fake company"))
                .andExpect(jsonPath("$.jiraAppConnected").value(true))
                .andExpect(jsonPath("$.jiraAppTokenReceivedAt").value("2026-11-17T03:00:00Z"))
                .andExpect(jsonPath("$.serviceAccountEmail").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).doesNotContain(TOKEN, CLOUD_ID);
    }

    @Test
    @DisplayName("AC-006.3 GET before setup returns 404 MSG60")
    void rejectsMissingWorkspace() throws Exception {
        perform(get("/api/v1/workspace"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG60"));
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-006.4 non-Admin GET returns 403")
    void rejectsNonAdminRead() throws Exception {
        perform(get("/api/v1/workspace")).andExpect(status().isForbidden());
    }

    private Map<String, Object> existingWorkspace() throws Exception {
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        clearInvocations(linker);
        return TwoTenantDatabases.tenantA().queryForMap("SELECT * FROM workspace");
    }

    private MockHttpServletRequestBuilder update(Map<String, ?> body) throws Exception {
        return put("/api/v1/workspace")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
    }

    private void assertUpdateUnchanged(Map<String, Object> before) {
        assertThat(TwoTenantDatabases.tenantA().queryForMap("SELECT * FROM workspace"))
                .isEqualTo(before);
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject(
                                "SELECT count(*) FROM audit_log_entry WHERE target_type = 'WORKSPACE' AND target_id = ?",
                                Integer.class,
                                TwoTenantDatabases.ACME))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-007.1 AC-007.6 PUT renames in the bound tenant only, calls no Jira, keeps the site and cloud id")
    void updatesWorkspace() throws Exception {
        existingWorkspace();
        perform(update(Map.of("companyName", "Renamed", "serviceAccountToken", "obviously-fake-new-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Renamed"))
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"));
        var row = TwoTenantDatabases.tenantA().queryForMap("SELECT * FROM workspace");
        assertThat(row.get("company_name")).isEqualTo("Renamed");
        assertThat(row.get("jira_cloud_id")).isEqualTo(CLOUD_ID);
        assertThat(row.get("service_account_api_token")).isNull();
        verifyNoInteractions(linker);
        assertThat(TwoTenantDatabases.tenantB().queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({"companyName,missing", "companyName,null", "companyName,blank", "companyName,number"})
    @DisplayName("AC-007.3 invalid required fields return MSG01 and preserve the row and audit")
    void rejectsRequiredUpdateFields(String field, String kind) throws Exception {
        var before = existingWorkspace();
        var body = new java.util.HashMap<String, Object>(input("Renamed", "https://fake.atlassian.net"));
        switch (kind) {
            case "missing" -> body.remove(field);
            case "null" -> body.put(field, null);
            case "blank" -> body.put(field, " \t\n ");
            case "number" -> body.put(field, 42);
            default -> throw new AssertionError(kind);
        }
        perform(update(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG01"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        assertUpdateUnchanged(before);
        verifyNoInteractions(linker);
    }

    @ParameterizedTest
    @ValueSource(strings = {"companyName"})
    @DisplayName("AC-007.3 an overlong company name returns MSG02 with max_length and changes nothing")
    void rejectsLongUpdateFields(String field) throws Exception {
        var before = existingWorkspace();
        var body = new java.util.HashMap<>(input("Renamed", "https://fake.atlassian.net"));
        body.put(field, "x".repeat(101));
        perform(update(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG02"))
                .andExpect(jsonPath("$.params.max_length").value(100))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        assertUpdateUnchanged(before);
        verifyNoInteractions(linker);
    }

    @Test
    @DisplayName("AC-007.7 the site, an email and a token in the update request are ignored")
    void ignoresSiteEmailAndToken() throws Exception {
        existingWorkspace();
        perform(update(Map.of(
                        "companyName", "Renamed",
                        "jiraSiteUrl", "http://localhost",
                        "serviceAccountEmail", "invalid",
                        "serviceAccountToken", "obviously-fake-new-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"));
        var row = TwoTenantDatabases.tenantA().queryForMap("SELECT * FROM workspace");
        assertThat(row.get("jira_site_address")).isEqualTo("https://fake.atlassian.net");
        assertThat(row.get("service_account_email")).isNull();
        assertThat(row.get("service_account_api_token")).isNull();
        verifyNoInteractions(linker);
    }

    @Test
    @DisplayName("AC-007.8 update writes one audit with the old and new company name")
    void auditsUpdate() throws Exception {
        existingWorkspace();
        perform(update(Map.of("companyName", "Renamed"))).andExpect(status().isOk());
        var entries = TwoTenantDatabases.tenantA()
                .queryForList(
                        "SELECT action, target_type, target_id, details::text AS details FROM audit_log_entry WHERE target_type = 'WORKSPACE' AND target_id = ?",
                        TwoTenantDatabases.ACME);
        assertThat(entries).hasSize(2);
        var updates = entries.stream()
                .filter(row -> ((String) row.get("details")).contains("\"old\""))
                .toList();
        assertThat(updates).singleElement().satisfies(entry -> {
            assertThat(entry.get("action")).isEqualTo("TRACKER_CONFIGURATION_CHANGE");
            assertThat(mapper.readTree((String) entry.get("details")))
                    .isEqualTo(
                            mapper.valueToTree(Map.of("companyName", Map.of("old", "Fake company", "new", "Renamed"))));
        });
    }

    @Test
    @DisplayName("AC-007.9 PUT before setup returns 404 MSG60 and stores nothing")
    void rejectsMissingUpdateWorkspace() throws Exception {
        perform(update(Map.of("companyName", "Renamed")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG60"));
        assertEmpty();
        verifyNoInteractions(linker);
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-007.10 non-Admin PUT returns 403 without changing data or calling Jira")
    void rejectsNonAdminUpdate() throws Exception {
        perform(update(input("Renamed", "https://fake.atlassian.net")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        assertEmpty();
        verifyNoInteractions(linker);
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
