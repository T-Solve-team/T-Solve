package vn.tsolve.tracker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;

@ExtendWith(OutputCaptureExtension.class)
class JiraClientTest {

    private static final JiraAccount ACCOUNT =
            new JiraAccount("https://acme.atlassian.net", "bot@acme.test", "secret-token");
    private static final String BASIC = "Basic "
            + Base64.getEncoder().encodeToString("bot@acme.test:secret-token".getBytes(StandardCharsets.UTF_8));
    private static final String COMMENTS =
            "https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment?orderBy=-created&maxResults=100";
    private static final String MYSELF = "https://acme.atlassian.net/rest/api/3/myself";
    private static final String PERMISSIONS = "https://acme.atlassian.net/rest/api/3/mypermissions"
            + "?permissions=BROWSE_PROJECTS,CREATE_ISSUES,MODIFY_REPORTER";
    private static final String ALL_PERMISSIONS = """
            {"permissions": {
              "BROWSE_PROJECTS": {"havePermission": true},
              "CREATE_ISSUES": {"havePermission": true},
              "MODIFY_REPORTER": {"havePermission": true}
            }}
            """;

    private MockRestServiceServer jira;
    private JiraClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        jira = MockRestServiceServer.bindTo(builder).build();
        client = new JiraClient(builder);
    }

    @Test
    @DisplayName("AC-007.1 checks identity then all three permissions with Basic auth")
    void checksServiceAccountWithBasicAuth() {
        jira.expect(requestTo(MYSELF))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", BASIC))
                .andRespond(withSuccess("{\"accountId\":\"bot-1\"}", MediaType.APPLICATION_JSON));
        jira.expect(requestTo(PERMISSIONS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", BASIC))
                .andRespond(withSuccess(ALL_PERMISSIONS, MediaType.APPLICATION_JSON));

        TicketTrackerClient tracker = client;
        assertThatCode(() -> tracker.checkServiceAccount(ACCOUNT)).doesNotThrowAnyException();
        jira.verify();
    }

    @DisplayName("AC-007.2 rejects authentication and authorization errors without leaking secrets")
    @ParameterizedTest(name = "AC-007.2 rejects {1} from {0} without exposing Jira body or credentials")
    @CsvSource({"myself,401", "myself,403", "mypermissions,401", "mypermissions,403"})
    void rejectsAuthenticationAndAuthorizationErrors(String endpoint, int status, CapturedOutput output) {
        expectIdentityBefore(endpoint);
        jira.expect(requestTo(endpoint.equals("myself") ? MYSELF : PERMISSIONS))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .body("private-jira-body " + ACCOUNT.email() + " " + ACCOUNT.apiToken()));

        assertThatThrownBy(() -> client.checkServiceAccount(ACCOUNT))
                .isInstanceOfSatisfying(JiraRejectedException.class, error -> assertThat(error.getStatus())
                        .isEqualTo(status))
                .hasMessage("Jira rejected the call: " + status)
                .hasNoCause();
        assertThat(output).doesNotContain("private-jira-body", ACCOUNT.email(), ACCOUNT.apiToken(), BASIC);
        jira.verify();
    }

    @DisplayName("AC-007.2 treats server errors as unavailable without leaking secrets")
    @ParameterizedTest(name = "AC-007.2 treats 500 from {0} as unavailable without exposing Jira body or credentials")
    @ValueSource(strings = {"myself", "mypermissions"})
    void treatsServerErrorsAsUnavailable(String endpoint, CapturedOutput output) {
        expectIdentityBefore(endpoint);
        jira.expect(requestTo(endpoint.equals("myself") ? MYSELF : PERMISSIONS))
                .andRespond(withServerError().body("private-jira-body " + ACCOUNT.email() + " " + ACCOUNT.apiToken()));

        assertThatThrownBy(() -> client.checkServiceAccount(ACCOUNT))
                .isInstanceOfSatisfying(JiraUnavailableException.class, error -> assertThat(error.getStatus())
                        .isEqualTo(500))
                .hasMessage("Jira unavailable: 500")
                .hasNoCause();
        assertThat(output).doesNotContain("private-jira-body", ACCOUNT.email(), ACCOUNT.apiToken(), BASIC);
        jira.verify();
    }

    @DisplayName("AC-007.1 rejects a false required permission")
    @ParameterizedTest(name = "AC-007.1 rejects false permission {0}")
    @ValueSource(strings = {"BROWSE_PROJECTS", "CREATE_ISSUES", "MODIFY_REPORTER"})
    void rejectsFalsePermission(String permission) {
        expectPermissions(ALL_PERMISSIONS.replace(
                "\"" + permission + "\": {\"havePermission\": true}",
                "\"" + permission + "\": {\"havePermission\": false}"));

        assertPermissionRejected();
    }

    @DisplayName("AC-007.1 rejects a missing required permission")
    @ParameterizedTest(name = "AC-007.1 rejects missing permission {0}")
    @ValueSource(strings = {"BROWSE_PROJECTS", "CREATE_ISSUES", "MODIFY_REPORTER"})
    void rejectsMissingPermission(String permission) {
        expectPermissions(ALL_PERMISSIONS.replace("\"" + permission + "\"", "\"UNRELATED_PERMISSION\""));

        assertPermissionRejected();
    }

    @DisplayName("AC-007.1 requires havePermission to be boolean true")
    @ParameterizedTest(name = "AC-007.1 rejects havePermission that is not boolean true: {0}")
    @ValueSource(
            strings = {"{}", "{\"havePermission\":null}", "{\"havePermission\":\"true\"}", "{\"havePermission\":1}"})
    void rejectsPermissionThatIsNotTrue(String permission) {
        expectPermissions(ALL_PERMISSIONS.replace(
                "\"MODIFY_REPORTER\": {\"havePermission\": true}", "\"MODIFY_REPORTER\": " + permission));

        assertPermissionRejected();
    }

    @DisplayName("AC-007.1 rejects absent permissions")
    @ParameterizedTest(name = "AC-007.1 rejects an absent permissions body: {0}")
    @ValueSource(strings = {"", "{}", "{\"permissions\":{}}"})
    void rejectsAbsentPermissions(String body) {
        expectPermissions(body);

        assertPermissionRejected();
    }

    @DisplayName("AC-007.2 treats timeouts as unavailable without leaking secrets")
    @ParameterizedTest(name = "AC-007.2 treats timeout from {0} as unavailable without exposing credentials")
    @ValueSource(strings = {"myself", "mypermissions"})
    void treatsCheckTimeoutAsUnavailable(String endpoint, CapturedOutput output) {
        expectIdentityBefore(endpoint);
        jira.expect(requestTo(endpoint.equals("myself") ? MYSELF : PERMISSIONS)).andRespond(request -> {
            throw new SocketTimeoutException("private-jira-body " + ACCOUNT.email() + " " + ACCOUNT.apiToken());
        });

        assertThatThrownBy(() -> client.checkServiceAccount(ACCOUNT))
                .isInstanceOfSatisfying(JiraUnavailableException.class, error -> assertThat(error.getStatus())
                        .isZero())
                .hasMessage("Jira unavailable: 0")
                .hasNoCause();
        assertThat(output).doesNotContain("private-jira-body", ACCOUNT.email(), ACCOUNT.apiToken(), BASIC);
        jira.verify();
    }

    @DisplayName("AC-007.1 requires HTTP 200 from both endpoints")
    @ParameterizedTest(name = "AC-007.1 rejects a non-200 response {1} from {0}")
    @CsvSource({"myself,204", "myself,302", "mypermissions,204", "mypermissions,302"})
    void rejectsNon200CheckResponse(String endpoint, int status) {
        expectIdentityBefore(endpoint);
        jira.expect(requestTo(endpoint.equals("myself") ? MYSELF : PERMISSIONS))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .body(ALL_PERMISSIONS)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.checkServiceAccount(ACCOUNT))
                .isInstanceOfSatisfying(JiraRejectedException.class, error -> assertThat(error.getStatus())
                        .isEqualTo(status))
                .hasNoCause();
        jira.verify();
    }

    private void expectIdentityBefore(String endpoint) {
        if (endpoint.equals("mypermissions")) {
            jira.expect(requestTo(MYSELF))
                    .andRespond(withSuccess("{\"accountId\":\"bot-1\"}", MediaType.APPLICATION_JSON));
        }
    }

    private void expectPermissions(String body) {
        expectIdentityBefore("mypermissions");
        jira.expect(requestTo(PERMISSIONS)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertPermissionRejected() {
        assertThatThrownBy(() -> client.checkServiceAccount(ACCOUNT))
                .isInstanceOfSatisfying(JiraRejectedException.class, error -> assertThat(error.getStatus())
                        .isEqualTo(403))
                .hasMessage("Jira rejected the call: 403")
                .hasNoCause();
        jira.verify();
    }

    @Test
    void addsTheCommentAsAdfParagraphsWithTheServiceAccount_AC_012_1() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", BASIC))
                .andExpect(content().json("""
                        {"body": {"type": "doc", "version": 1, "content": [
                          {"type": "paragraph", "content": [{"type": "text", "text": "T-Solve suggestion:"}]},
                          {"type": "paragraph", "content": [{"type": "text", "text": "Restart the VPN."}]}
                        ]}}
                        """, true))
                .andRespond(withSuccess("{\"id\": \"10042\"}", MediaType.APPLICATION_JSON));

        String id = client.addComment(ACCOUNT, "ITSUP-1", List.of("T-Solve suggestion:", "Restart the VPN."));

        assertThat(id).isEqualTo("10042");
        jira.verify();
    }

    @Test
    void findsTheServiceAccountsCommentThatStartsWithThePrefix_AC_012_5() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/myself"))
                .andExpect(header("Authorization", BASIC))
                .andRespond(withSuccess("{\"accountId\": \"bot-1\"}", MediaType.APPLICATION_JSON));
        jira.expect(requestTo(COMMENTS))
                .andRespond(withSuccess(
                        """
                        {"comments": [
                          {"id": "1", "author": {"accountId": "someone"}, "body": %s},
                          {"id": "2", "author": {"accountId": "bot-1"}, "body": %s},
                          {"id": "3", "author": {"accountId": "bot-1"}, "body": %s}
                        ]}
                        """.formatted(
                                        adf("T-Solve suggestion: copied by a person"),
                                        adf("Another bot note"),
                                        adf("T-Solve suggestion:")),
                        MediaType.APPLICATION_JSON));

        assertThat(client.findComment(ACCOUNT, "ITSUP-1", "T-Solve suggestion:"))
                .contains("3");
    }

    @Test
    void findsNothingWhenNoCommentMatches_AC_012_5() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/myself"))
                .andRespond(withSuccess("{\"accountId\": \"bot-1\"}", MediaType.APPLICATION_JSON));
        jira.expect(requestTo(COMMENTS)).andRespond(withSuccess("{\"comments\": []}", MediaType.APPLICATION_JSON));

        assertThat(client.findComment(ACCOUNT, "ITSUP-1", "T-Solve suggestion:"))
                .isEmpty();
    }

    @Test
    void treatsACreatedCommentWithoutIdAsUnavailable_AC_012_3_BUG_167() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .isInstanceOf(JiraUnavailableException.class);
    }

    @Test
    void treatsAServiceAccountWithoutIdAsUnavailable_AC_012_5_BUG_168() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/myself"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findComment(ACCOUNT, "ITSUP-1", "T-Solve suggestion:"))
                .isInstanceOf(JiraUnavailableException.class);
    }

    @Test
    void reportsServerErrorsAndRateLimitsAsUnavailable_AC_012_7() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(withServerError().body("internal: Restart the VPN."));

        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("500")
                .hasMessageNotContaining("Restart the VPN")
                .hasNoCause();

        jira.reset();
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("429");
    }

    @Test
    void reportsATimeoutAsUnavailable_AC_012_7() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .isInstanceOf(JiraUnavailableException.class)
                .hasNoCause();
    }

    @Test
    void reportsAuthenticationPermissionAndMissingIssueAsRejected_AC_012_8() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(withUnauthorizedRequest());
        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .isInstanceOf(JiraRejectedException.class)
                .hasMessageContaining("401");

        jira.reset();
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .isInstanceOf(JiraRejectedException.class)
                .hasMessageContaining("403");

        jira.reset();
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .isInstanceOf(JiraRejectedException.class)
                .hasMessageContaining("404");
    }

    @Test
    void neverPutsTheEmailOrTokenInAnError_NF_9() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment"))
                .andRespond(withUnauthorizedRequest().body("bad credentials for bot@acme.test"));

        assertThatThrownBy(() -> client.addComment(ACCOUNT, "ITSUP-1", List.of("x")))
                .hasMessageNotContaining("bot@acme.test")
                .hasMessageNotContaining("secret-token");
    }

    private static String adf(String text) {
        return """
                {"type": "doc", "version": 1, "content": [
                  {"type": "paragraph", "content": [{"type": "text", "text": "%s"}]}]}
                """.formatted(text);
    }
}
