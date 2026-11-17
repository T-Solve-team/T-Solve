package vn.tsolve.chatbot.issue.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.tsolve.chatbot.exception.ChatbotJiraException;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator.CreatedIssue;
import vn.tsolve.chatbot.issue.client.JiraIssueCreator.NewIssue;
import vn.tsolve.tracker.dto.JiraAccount;

@ExtendWith(OutputCaptureExtension.class)
class JiraRestIssueCreatorTest {
    private static final String CLOUD = "12345678-1234-1234-1234-123456789abc";
    private static final String URL = "https://api.atlassian.com/ex/jira/" + CLOUD + "/rest/api/3/issue";
    private static final String SITE = "https://workspace.atlassian.net";
    private static final String TOKEN = "token-value";
    private static final String SUMMARY = "summary-text";
    private static final String DESCRIPTION = "description-text";
    private static final JiraAccount ACCOUNT = new JiraAccount(SITE, CLOUD, TOKEN);
    private static final NewIssue ISSUE = new NewIssue("HELP", SUMMARY, DESCRIPTION, "linked-reporter");
    private MockRestServiceServer jira;
    private JiraRestIssueCreator creator;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        jira = MockRestServiceServer.bindTo(builder).build();
        creator = new JiraRestIssueCreator(builder);
    }

    @AfterEach
    void verifyRequests() {
        jira.verify();
    }

    @Test
    void postsGatewayBearerTaskLabelsReporterAndAdf_AC_011_1() {
        jira.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"fields":{
                          "project":{"key":"HELP"},"issuetype":{"name":"Task"},
                          "summary":"Summary as reviewed",
                          "description":{"type":"doc","version":1,"content":[
                            {"type":"paragraph","content":[{"type":"text","text":"First line"}]},
                            {"type":"paragraph","content":[{"type":"text","text":"  Second <line> & data"}]}
                          ]},
                          "labels":["tsolve-chatbot"],"reporter":{"id":"linked-reporter"}
                        }}
                        """, true))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"key\":\"HELP-12\"}"));
        assertThat(creator.create(
                        ACCOUNT,
                        new NewIssue(
                                "HELP",
                                "Summary as reviewed",
                                "First line\r\n \r\n  Second <line> & data\n",
                                "linked-reporter")))
                .isEqualTo(new CreatedIssue("HELP-12", SITE + "/browse/HELP-12"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  \n\t"})
    void omitsEmptyOrBlankDescription_AC_011_1(String description) {
        jira.expect(requestTo(URL))
                .andExpect(jsonPath("$.fields.description").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"key\":\"HELP-12\"}"));
        assertThat(creator.create(ACCOUNT, new NewIssue("HELP", SUMMARY, description, "linked-reporter"))
                        .issueKey())
                .isEqualTo("HELP-12");
    }

    @ParameterizedTest
    @ValueSource(strings = {SITE, SITE + "/", SITE + "///"})
    void buildsIssueUrlWithoutTrailingSlashes_AC_011_1(String site) {
        jira.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"key\":\"HELP-12\"}"));
        assertThat(creator.create(new JiraAccount(site, CLOUD, TOKEN), ISSUE).issueUrl())
                .isEqualTo(SITE + "/browse/HELP-12");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "cloud-id", "1-1-1-1-1", "../other-cloud", CLOUD + "/rest/api/3/issue"})
    void rejectsNonCanonicalCloudIdWithoutHttpCall_AC_011_6(String cloudId) {
        assertThatThrownBy(() -> creator.create(new JiraAccount(SITE, cloudId, TOKEN), ISSUE))
                .isInstanceOfSatisfying(ChatbotJiraException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("MSG148");
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(failure.getCause()).isNull();
                });
    }

    @ParameterizedTest
    @MethodSource("refusedRequests")
    void mapsReporterUnspecifiedAndOtherClientRefusals_AC_011_6(int status, String body) {
        refuse(status, body);
        assertFailure("MSG148", HttpStatus.BAD_GATEWAY);
    }

    private static Stream<Arguments> refusedRequests() {
        return Stream.of(
                Arguments.of(400, "{\"errors\":{\"reporter\":\"refused\"}}"),
                Arguments.of(400, "{\"errors\":{\"summary\":\"refused\"}}"),
                Arguments.of(400, "{\"errors\":{\"description\":\"refused\"}}"),
                Arguments.of(400, "{\"errors\":{\"labels\":\"refused\"}}"),
                Arguments.of(400, "{\"errorMessages\":[\"refused\"]}"),
                Arguments.of(400, "{\"errors\":{}}"),
                Arguments.of(
                        400,
                        "{\"errors\":{\"summary\":\"refused\",\"description\":\"refused\",\"labels\":\"refused\"}}"),
                Arguments.of(400, "not json"),
                Arguments.of(401, "{}"),
                Arguments.of(403, "{}"),
                Arguments.of(404, "{}"),
                Arguments.of(409, "{}"),
                Arguments.of(422, "{}"),
                Arguments.of(429, "{}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuetype", "priority", "project", "customfield_10001"})
    void mapsMisconfiguredTaskOrRequiredField_AC_011_7(String field) {
        refuse(400, "{\"errors\":{\"" + field + "\":\"required\",\"summary\":\"refused\"}}");
        assertFailure("MSG157", HttpStatus.BAD_GATEWAY);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void mapsServerFailureWithoutRetry_AC_011_8(int status) {
        refuse(status, "{\"errors\":{\"reporter\":\"refused\"}}");
        assertFailure("MSG149", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsReadTimeoutWithoutRetryOrLeakingCause_AC_011_8() {
        jira.expect(requestTo(URL))
                .andRespond(withException(new SocketTimeoutException(TOKEN + SUMMARY + DESCRIPTION)));
        assertFailure("MSG149", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsIoFailureWithoutRetryOrLeakingCause_AC_011_8() {
        jira.expect(requestTo(URL)).andRespond(withException(new IOException(TOKEN + SUMMARY + DESCRIPTION)));
        assertFailure("MSG149", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void logsOnlyStatusAndRefusedFieldNames_AC_011_10(CapturedOutput output) {
        refuse(
                400,
                "{\"errors\":{\"reporter\":\"" + TOKEN + SUMMARY + DESCRIPTION
                        + "\"},\"errorMessages\":[\"error-message-text\"]}");
        assertFailure("MSG148", HttpStatus.BAD_GATEWAY);
        assertThat(output.getAll())
                .contains("status=400", "errors=[reporter]")
                .doesNotContain(TOKEN, SUMMARY, DESCRIPTION, "error-message-text", "RestClientResponseException");
    }

    @Test
    void logsNoSensitiveTransportFailureOrSuccessfulContent_AC_011_10(CapturedOutput output) {
        jira.expect(requestTo(URL)).andRespond(withException(new IOException(TOKEN + SUMMARY + DESCRIPTION)));
        jira.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"key\":\"HELP-12\"}"));
        assertFailure("MSG149", HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(creator.create(ACCOUNT, ISSUE).issueKey()).isEqualTo("HELP-12");
        assertThat(output.getAll()).doesNotContain(TOKEN, SUMMARY, DESCRIPTION, "IOException");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"key\":null}", "{\"key\":\" \"}", "{\"key\":123}", "not json", ""})
    void mapsUnusableCreatedBodyWithoutRetry_AC_011_6_AC_011_8(String body) {
        jira.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
        assertFailure("MSG149", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 202, 204})
    void mapsNonCreatedSuccessWithoutRetry_AC_011_8(int status) {
        jira.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"key\":\"HELP-12\"}"));
        assertFailure("MSG149", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void refuse(int status, String body) {
        jira.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
    }

    private void assertFailure(String code, HttpStatus status) {
        assertThatThrownBy(() -> creator.create(ACCOUNT, ISSUE))
                .isInstanceOfSatisfying(ChatbotJiraException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo(code);
                    assertThat(failure.getStatus()).isEqualTo(status);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getMessage()).doesNotContain(TOKEN, SUMMARY, DESCRIPTION);
                });
    }
}
