package vn.tsolve.tracker.client;

import static org.assertj.core.api.Assertions.assertThat;
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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerProjectLookup.Outcome;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;
import vn.tsolve.tracker.service.impl.TrackerProjectServiceImpl;

class JiraClientTest {

    private static final JiraAccount ACCOUNT =
            new JiraAccount("https://acme.atlassian.net", "bot@acme.test", "secret-token");
    private static final String BASIC = "Basic "
            + Base64.getEncoder().encodeToString("bot@acme.test:secret-token".getBytes(StandardCharsets.UTF_8));
    private static final String COMMENTS =
            "https://acme.atlassian.net/rest/api/3/issue/ITSUP-1/comment?orderBy=-created&maxResults=100";

    private MockRestServiceServer jira;
    private JiraClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        jira = MockRestServiceServer.bindTo(builder).build();
        client = new JiraClient(builder);
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

    @Test
    void readsProjectAndStatusesWithBasicAuthentication_AC_013_1_AC_013_10() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/itsup"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", BASIC))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"key\":\"ITSUP\",\"name\":\"IT\"}", MediaType.APPLICATION_JSON));
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", BASIC))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(
                        withSuccess("[{\"statuses\":[{\"id\":\"1\",\"name\":\"Open\"}]}]", MediaType.APPLICATION_JSON));

        assertThat(client.findProject(ACCOUNT, "itsup")).contains("ITSUP");
        assertThat(client.projectStatuses(ACCOUNT, "ITSUP")).containsExactly(new TrackerStatus("1", "Open", false));
        jira.verify();
    }

    @Test
    void flattensStatusesWithoutDeduplicatingAndMapsExactDoneCategory_AC_013_2_AC_013_3() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andRespond(withSuccess("""
                    [
                      {"statuses":[
                        {"id":"1","name":"Open","statusCategory":{"key":"indeterminate"}},
                        {"id":"2","name":"Done","statusCategory":{"key":"done"}},
                        {"id":"3","name":"Review"}
                      ]},
                      {"statuses":[
                        {"id":"1","name":"Reopened","statusCategory":{"key":"done"}},
                        {"id":"004","name":"Case sensitive","statusCategory":{"key":"Done"}},
                        {"id":"5","name":"Empty category","statusCategory":{}},
                        {"id":"6","name":"Trailing space","statusCategory":{"key":"done "}}
                      ]}
                    ]
                    """, MediaType.APPLICATION_JSON));

        assertThat(client.projectStatuses(ACCOUNT, "ITSUP"))
                .containsExactly(
                        new TrackerStatus("1", "Open", false),
                        new TrackerStatus("2", "Done", true),
                        new TrackerStatus("3", "Review", false),
                        new TrackerStatus("1", "Reopened", true),
                        new TrackerStatus("004", "Case sensitive", false),
                        new TrackerStatus("5", "Empty category", false),
                        new TrackerStatus("6", "Trailing space", false));
        jira.verify();
    }

    @Test
    void acceptsAnEmptyStatusesArray_AC_013_9() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.projectStatuses(ACCOUNT, "ITSUP")).isEmpty();
        jira.verify();
    }

    @Test
    void returnsEmptyWhenTheProjectDoesNotExist_AC_013_4() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP"))
                .andRespond(withResourceNotFound());

        assertThat(client.findProject(ACCOUNT, "ITSUP")).isEmpty();
        jira.verify();
    }

    @Test
    void rejectsMissingStatusesWithTheExistingStatusMapping_AC_013_4() {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.projectStatuses(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraRejectedException.class, failure -> assertThat(failure.getStatus())
                        .isEqualTo(404))
                .hasNoCause();
        jira.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 400})
    void rejectsProjectClientErrors_AC_013_6(int status) {
        jira.reset();
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.findProject(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraRejectedException.class, failure -> assertThat(failure.getStatus())
                        .isEqualTo(status))
                .hasNoCause();
        jira.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 400})
    void rejectsStatusesClientErrors_AC_013_6(int status) {
        jira.reset();
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.projectStatuses(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraRejectedException.class, failure -> assertThat(failure.getStatus())
                        .isEqualTo(status))
                .hasNoCause();
        jira.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 429})
    void reportsProjectServerErrorsAndRateLimitsAsUnavailable_AC_013_7(int status) {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.findProject(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isEqualTo(status))
                .hasNoCause();
        jira.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 429})
    void reportsStatusesServerErrorsAndRateLimitsAsUnavailable_AC_013_7(int status) {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.projectStatuses(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isEqualTo(status))
                .hasNoCause();
        jira.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsTimeoutsAndUnreachableJiraWithoutLeakingCauses_AC_013_7(boolean statusesRead) {
        String url = "https://acme.atlassian.net/rest/api/3/project/ITSUP" + (statusesRead ? "/statuses" : "");
        for (IOException transportFailure :
                List.of(new SocketTimeoutException("Read timed out"), new IOException("Connection refused"))) {
            jira.reset();
            jira.expect(requestTo(url)).andRespond(request -> {
                throw transportFailure;
            });

            assertThatThrownBy(() -> {
                        if (statusesRead) {
                            client.projectStatuses(ACCOUNT, "ITSUP");
                        } else {
                            client.findProject(ACCOUNT, "ITSUP");
                        }
                    })
                    .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                            .isZero())
                    .hasNoCause();
            jira.verify();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"key\":\"\"}",
                "{\"key\":\"   \"}",
                "{\"key\":12}",
                "{\"key\":null}",
                "null",
                "",
                "{not json"
            })
    void reportsMissingInvalidOrUnreadableProjectKeysAsUnavailable_AC_013_8(String body) {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findProject(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isZero())
                .hasNoCause();
        jira.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "[{\"statuses\":{}}]",
                "[{}]",
                "[{\"statuses\":[{\"name\":\"Open\"}]}]",
                "[{\"statuses\":[{\"id\":\"\",\"name\":\"Open\"}]}]",
                "[{\"statuses\":[{\"id\":\"   \",\"name\":\"Open\"}]}]",
                "[{\"statuses\":[{\"id\":1,\"name\":\"Open\"}]}]",
                "[{\"statuses\":[{\"id\":\"1\"}]}]",
                "[{\"statuses\":[{\"id\":\"1\",\"name\":\"\"}]}]",
                "[{\"statuses\":[{\"id\":\"1\",\"name\":\"   \"}]}]",
                "[{\"statuses\":[{\"id\":\"1\",\"name\":12}]}]",
                "[{\"statuses\":[{\"id\":\"1\",\"name\":null}]}]",
                "null",
                "",
                "{not json"
            })
    void reportsInvalidOrUnreadableStatusesAsUnavailable_AC_013_8(String body) {
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.projectStatuses(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isZero())
                .hasNoCause();
        jira.verify();
    }

    @Test
    void stripsCredentialsAndResponseBodiesFromBothReadFailures_AC_013_12_AC_013_13() {
        String body = "bot@acme.test secret-token JIRA-BODY-SECRET";
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP"))
                .andRespond(withUnauthorizedRequest().body(body));

        assertThatThrownBy(() -> client.findProject(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraRejectedException.class, failure -> assertThat(failure.getStatus())
                        .isEqualTo(401))
                .hasMessageNotContaining("bot@acme.test")
                .hasMessageNotContaining("secret-token")
                .hasMessageNotContaining("JIRA-BODY-SECRET")
                .hasNoCause();
        jira.verify();

        jira.reset();
        jira.expect(requestTo("https://acme.atlassian.net/rest/api/3/project/ITSUP/statuses"))
                .andRespond(withServerError().body(body));

        assertThatThrownBy(() -> client.projectStatuses(ACCOUNT, "ITSUP"))
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isEqualTo(500))
                .hasMessageNotContaining("bot@acme.test")
                .hasMessageNotContaining("secret-token")
                .hasMessageNotContaining("JIRA-BODY-SECRET")
                .hasNoCause();
        jira.verify();
    }

    @Test
    void appliesConnectAndReadTimeoutsToTheRequestFactory_AC_013_10() throws NoSuchMethodException {
        var parameters =
                JiraClient.class.getConstructor(Duration.class, Duration.class).getParameters();

        assertThat(parameters[0].getAnnotation(Value.class).value())
                .isEqualTo("${tsolve.tracker.jira.connect-timeout:5s}");
        assertThat(parameters[1].getAnnotation(Value.class).value())
                .isEqualTo("${tsolve.tracker.jira.read-timeout:10s}");

        JiraClient configuredClient = new JiraClient(Duration.ofSeconds(3), Duration.ofSeconds(7));
        Object builder = ReflectionTestUtils.getField(configuredClient, "builder");
        assertThat(builder).isNotNull();
        Object factory = ReflectionTestUtils.getField(builder, "requestFactory");
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(3000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(7000);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsSchemeLessSiteAsUnavailable_AC_013_7_AC_013_8_BUG_203(boolean statusesRead) {
        JiraAccount account = new JiraAccount("acme.atlassian.net", ACCOUNT.email(), ACCOUNT.apiToken());
        // The mock factory accepts relative URIs; the production factory rejects them before opening a connection.
        JiraClient realClient = new JiraClient(Duration.ofSeconds(3), Duration.ofSeconds(7));

        assertThatThrownBy(() -> {
                    if (statusesRead) {
                        realClient.projectStatuses(account, "ITSUP");
                    } else {
                        realClient.findProject(account, "ITSUP");
                    }
                })
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isZero())
                .hasNoCause()
                .hasMessageNotContaining(account.email())
                .hasMessageNotContaining(account.apiToken());
    }

    @ParameterizedTest
    @CsvSource({
        "bot:automation@acme.test, false",
        "bot:automation@acme.test, true",
        "бот@acme.test, false",
        "бот@acme.test, true"
    })
    void reportsMalformedEmailAsUnavailable_AC_013_7_AC_013_8_BUG_203(String email, boolean statusesRead) {
        JiraAccount account = new JiraAccount(ACCOUNT.siteUrl(), email, ACCOUNT.apiToken());

        assertThatThrownBy(() -> {
                    if (statusesRead) {
                        client.projectStatuses(account, "ITSUP");
                    } else {
                        client.findProject(account, "ITSUP");
                    }
                })
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isZero())
                .hasNoCause()
                .hasMessageNotContaining(email)
                .hasMessageNotContaining(account.apiToken());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsInvalidContentTypeAsUnavailable_AC_013_7_AC_013_8_BUG_203(boolean statusesRead) {
        String url = ACCOUNT.siteUrl() + "/rest/api/3/project/ITSUP" + (statusesRead ? "/statuses" : "");
        jira.expect(requestTo(url))
                .andRespond(withStatus(HttpStatus.OK)
                        .header("Content-Type", "not a media type")
                        .body(statusesRead ? "[]" : "{\"key\":\"ITSUP\"}"));

        assertThatThrownBy(() -> {
                    if (statusesRead) {
                        client.projectStatuses(ACCOUNT, "ITSUP");
                    } else {
                        client.findProject(ACCOUNT, "ITSUP");
                    }
                })
                .isInstanceOfSatisfying(JiraUnavailableException.class, failure -> assertThat(failure.getStatus())
                        .isZero())
                .hasNoCause()
                .hasMessageNotContaining(ACCOUNT.email())
                .hasMessageNotContaining(ACCOUNT.apiToken());
        jira.verify();
    }

    @Test
    void returnsUNAVAILABLEWithoutPartialProjectForNonHttpScheme_AC_013_7_BUG_203() {
        JiraAccount account = new JiraAccount("ftp://example.atlassian.net", ACCOUNT.email(), ACCOUNT.apiToken());
        JiraClient realClient = new JiraClient(Duration.ofSeconds(3), Duration.ofSeconds(7));

        TrackerProjectLookup lookup =
                new TrackerProjectServiceImpl(() -> Optional.of(account), realClient).findProject("ITSUP");

        assertThat(lookup.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(lookup.key()).isNull();
        assertThat(lookup.statuses()).isEmpty();
    }

    @Test
    void returnsUNAVAILABLEWithoutPartialProjectForInvalidContentType_AC_013_7_AC_013_8_BUG_203() {
        JiraAccount account = ACCOUNT;
        jira.expect(requestTo(account.siteUrl() + "/rest/api/3/project/ITSUP"))
                .andRespond(withStatus(HttpStatus.OK)
                        .header("Content-Type", "not a media type")
                        .body("{\"key\":\"ITSUP\"}"));

        TrackerProjectLookup lookup =
                new TrackerProjectServiceImpl(() -> Optional.of(account), client).findProject("ITSUP");

        assertThat(lookup.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(lookup.key()).isNull();
        assertThat(lookup.statuses()).isEmpty();
        jira.verify();
    }

    private static String adf(String text) {
        return """
                {"type": "doc", "version": 1, "content": [
                  {"type": "paragraph", "content": [{"type": "text", "text": "%s"}]}]}
                """.formatted(text);
    }
}
