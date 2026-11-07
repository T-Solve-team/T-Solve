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

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;

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
                .andExpect(content()
                        .json(
                                """
                        {"body": {"type": "doc", "version": 1, "content": [
                          {"type": "paragraph", "content": [{"type": "text", "text": "T-Solve suggestion:"}]},
                          {"type": "paragraph", "content": [{"type": "text", "text": "Restart the VPN."}]}
                        ]}}
                        """,
                                true))
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
                        """
                                .formatted(
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
                """
                .formatted(text);
    }
}
