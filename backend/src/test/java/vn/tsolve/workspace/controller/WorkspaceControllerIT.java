package vn.tsolve.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import vn.tsolve.common.lookup.WorkspaceStatusLookup;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.WithTSolveUser;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.tracker.client.TicketTrackerClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
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

    private Map<String, Object> existingWorkspace() throws Exception {
        perform(setup(input("Fake company", "https://fake.atlassian.net"))).andExpect(status().isCreated());
        clearInvocations(tracker, linker);
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
    @DisplayName("AC-003.1 PUT persists encrypted changes only in the bound tenant and returns public fields")
    void updatesWorkspace() throws Exception {
        existingWorkspace();
        String token = "obviously-fake-new-token";
        String body = perform(update(Map.of(
                        "companyName",
                        "Renamed",
                        "serviceAccountEmail",
                        "new@example.test",
                        "serviceAccountToken",
                        token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Renamed"))
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"))
                .andExpect(jsonPath("$.serviceAccountEmail").value("new@example.test"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(mapper.readTree(body).size()).isEqualTo(3);
        assertThat(body).doesNotContain(TOKEN, token, "Token", "token");
        var row = TwoTenantDatabases.tenantA().queryForMap("SELECT * FROM workspace");
        assertThat(row.get("company_name")).isEqualTo("Renamed");
        assertThat(row.get("service_account_email")).isEqualTo("new@example.test");
        String encrypted = (String) row.get("service_account_api_token");
        assertThat(encrypted).doesNotContain(token);
        assertThat(cipher.decrypt(TwoTenantDatabases.ACME, encrypted)).isEqualTo(token);
        assertThat(TwoTenantDatabases.tenantB().queryForObject("SELECT count(*) FROM workspace", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "null", "", " \t\n "})
    @DisplayName("AC-003.2 omitted, null and blank update tokens retain the identical stored ciphertext")
    void keepsUpdateToken(String token) throws Exception {
        var before = existingWorkspace();
        var body = new java.util.HashMap<String, Object>(
                Map.of("companyName", "Renamed", "serviceAccountEmail", "fake@example.test"));
        if (!token.equals("absent")) body.put("serviceAccountToken", token.equals("null") ? null : token);
        perform(update(body)).andExpect(status().isOk());
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject("SELECT service_account_api_token FROM workspace", String.class))
                .isEqualTo(before.get("service_account_api_token"));
        verifyNoInteractions(tracker, linker);
    }

    @ParameterizedTest
    @CsvSource({
        "companyName,missing",
        "companyName,null",
        "companyName,blank",
        "companyName,number",
        "serviceAccountEmail,missing",
        "serviceAccountEmail,null",
        "serviceAccountEmail,blank",
        "serviceAccountEmail,number"
    })
    @DisplayName("AC-003.3 invalid required fields return MSG01 and preserve the row and audit")
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
        verifyNoInteractions(tracker, linker);
    }

    @ParameterizedTest
    @ValueSource(strings = {"companyName", "serviceAccountEmail"})
    @DisplayName("AC-003.3 overlong update fields return MSG02 with max_length and change nothing")
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
        verifyNoInteractions(tracker, linker);
    }

    @Test
    @DisplayName("AC-003.3 invalid update email returns MSG03 without changing data")
    void rejectsUpdateEmail() throws Exception {
        var before = existingWorkspace();
        perform(update(Map.of("companyName", "Renamed", "serviceAccountEmail", "invalid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG03"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("serviceAccountEmail"));
        assertUpdateUnchanged(before);
        verifyNoInteractions(tracker, linker);
    }

    @Test
    @DisplayName("AC-003.4 changed email with blank token checks the stored site and decrypted stored token")
    void checksUpdateEmail() throws Exception {
        var before = existingWorkspace();
        perform(update(Map.of(
                        "companyName",
                        "Renamed",
                        "serviceAccountEmail",
                        "new@example.test",
                        "serviceAccountToken",
                        " \t ")))
                .andExpect(status().isOk());
        verify(tracker).checkServiceAccount(new JiraAccount("https://fake.atlassian.net", "new@example.test", TOKEN));
        assertThat(TwoTenantDatabases.tenantA()
                        .queryForObject("SELECT service_account_api_token FROM workspace", String.class))
                .isEqualTo(before.get("service_account_api_token"));
        verifyNoInteractions(linker);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 0})
    @DisplayName("AC-003.5 Jira failure returns MSG156 on token without changing any stored value or audit")
    void rejectsUpdateCheck(int code) throws Exception {
        var before = existingWorkspace();
        RuntimeException failure =
                code == 401 || code == 403 ? new JiraRejectedException(code) : new JiraUnavailableException(code);
        doThrow(failure).when(tracker).checkServiceAccount(any());
        String body = perform(update(Map.of(
                        "companyName",
                        "Renamed",
                        "serviceAccountEmail",
                        "new@example.test",
                        "serviceAccountToken",
                        "obviously-fake-new-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MSG156"))
                .andExpect(jsonPath("$.params.tracker_name").value("Jira"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("serviceAccountToken"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body)
                .doesNotContain(TOKEN, "obviously-fake-new-token", (String) before.get("service_account_api_token"));
        assertUpdateUnchanged(before);
        verifyNoInteractions(linker);
    }

    @Test
    @DisplayName("AC-003.6 company-only rename succeeds while Jira is unavailable")
    void renamesWithoutJira() throws Exception {
        existingWorkspace();
        doThrow(new JiraUnavailableException(0)).when(tracker).checkServiceAccount(any());
        perform(update(Map.of("companyName", "Renamed", "serviceAccountEmail", "fake@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Renamed"));
        verifyNoInteractions(tracker, linker);
    }

    @Test
    @DisplayName("AC-003.7 request site is ignored and never used in the account check")
    void ignoresUpdateSite() throws Exception {
        existingWorkspace();
        perform(update(input("Renamed", "http://localhost")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraSiteUrl").value("https://fake.atlassian.net"));
        assertThat(TwoTenantDatabases.tenantA().queryForObject("SELECT jira_site_address FROM workspace", String.class))
                .isEqualTo("https://fake.atlassian.net");
        verify(tracker).checkServiceAccount(new JiraAccount("https://fake.atlassian.net", "fake@example.test", TOKEN));
        verifyNoInteractions(linker);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("AC-003.8 update writes one audit with old/new public values and tokenChanged without secrets")
    void auditsUpdate(boolean tokenChanged) throws Exception {
        var before = existingWorkspace();
        var body = new java.util.HashMap<>(input("Renamed", "https://fake.atlassian.net"));
        body.put("serviceAccountEmail", "new@example.test");
        if (!tokenChanged) body.remove("serviceAccountToken");
        perform(update(body)).andExpect(status().isOk());
        var entries = TwoTenantDatabases.tenantA()
                .queryForList(
                        "SELECT action, target_type, target_id, details::text AS details FROM audit_log_entry WHERE target_type = 'WORKSPACE' AND target_id = ?",
                        TwoTenantDatabases.ACME);
        assertThat(entries).hasSize(2);
        var updates = entries.stream()
                .filter(row -> ((String) row.get("details")).contains("tokenChanged"))
                .toList();
        assertThat(updates).hasSize(1);
        var entry = updates.getFirst();
        assertThat(entry.get("action")).isEqualTo("TRACKER_CONFIGURATION_CHANGE");
        assertThat(entry.get("target_id")).isEqualTo(TwoTenantDatabases.ACME);
        assertThat(mapper.readTree((String) entry.get("details")))
                .isEqualTo(mapper.valueToTree(Map.of(
                        "companyName", Map.of("old", "Fake company", "new", "Renamed"),
                        "serviceAccountEmail", Map.of("old", "fake@example.test", "new", "new@example.test"),
                        "tokenChanged", tokenChanged)));
        assertThat(entry.toString()).doesNotContain(TOKEN, (String) before.get("service_account_api_token"));
        if (tokenChanged)
            verify(tracker)
                    .checkServiceAccount(new JiraAccount("https://fake.atlassian.net", "new@example.test", TOKEN));
    }

    @Test
    @DisplayName("AC-003.9 PUT before setup returns 404 MSG60 and stores nothing")
    void rejectsMissingUpdateWorkspace() throws Exception {
        perform(update(Map.of("companyName", "Renamed", "serviceAccountEmail", "fake@example.test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MSG60"));
        assertEmpty();
        verifyNoInteractions(tracker, linker);
    }

    @Test
    @WithTSolveUser
    @DisplayName("AC-003.10 non-Admin PUT returns 403 without changing data or calling Jira")
    void rejectsNonAdminUpdate() throws Exception {
        perform(update(input("Renamed", "https://fake.atlassian.net")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        assertEmpty();
        verifyNoInteractions(tracker, linker);
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
