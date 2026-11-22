package vn.tsolve.jiraapp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.tsolve.tracker.dto.JiraAccount;

@ExtendWith(OutputCaptureExtension.class)
class RestJiraUserClientTest {

    private static final String CLOUD = "12345678-1234-1234-1234-123456789abc";
    private static final String URL =
            "https://api.atlassian.com/ex/jira/" + CLOUD + "/rest/api/3/user?accountId=jira-1";
    private static final String TOKEN = "app-token-value";
    private static final JiraAccount ACCOUNT = new JiraAccount("https://acme.atlassian.net", CLOUD, TOKEN);

    private MockRestServiceServer jira;
    private RestJiraUserClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        jira = MockRestServiceServer.bindTo(builder).build();
        client = new RestJiraUserClient(builder);
    }

    @AfterEach
    void verifyRequests() {
        jira.verify();
    }

    @Test
    void readsTheDisplayNameWithTheAppToken_AC_014_6() {
        jira.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        "{\"accountId\":\"jira-1\",\"displayName\":\" An Nguyen \"}", MediaType.APPLICATION_JSON));

        assertThat(client.displayName(ACCOUNT, "jira-1")).contains("An Nguyen");
    }

    @Test
    void givesNothingWhenJiraReturnsNoDisplayName_AC_014_9() {
        jira.expect(requestTo(URL))
                .andRespond(withSuccess("{\"accountId\":\"jira-1\",\"displayName\":\"\"}", MediaType.APPLICATION_JSON));

        assertThat(client.displayName(ACCOUNT, "jira-1")).isEmpty();
    }

    @Test
    void givesNothingWhenJiraRefusesAndLogsNoToken_AC_014_9(CapturedOutput output) {
        jira.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"errorMessages\":[\"x\"]}"));

        assertThat(client.displayName(ACCOUNT, "jira-1")).isEmpty();
        assertThat(output).contains("status=403").doesNotContain(TOKEN);
    }

    @Test
    void givesNothingWhenJiraCannotBeReached_AC_014_9(CapturedOutput output) {
        jira.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timeout " + TOKEN)));

        assertThat(client.displayName(ACCOUNT, "jira-1")).isEmpty();
        assertThat(output).doesNotContain(TOKEN);
    }

    @Test
    void callsNothingForABadCloudIdOrABlankAccount_AC_014_9() {
        assertThat(client.displayName(new JiraAccount("https://acme.atlassian.net", "not-a-cloud", TOKEN), "jira-1"))
                .isEmpty();
        assertThat(client.displayName(ACCOUNT, " ")).isEmpty();
    }
}
