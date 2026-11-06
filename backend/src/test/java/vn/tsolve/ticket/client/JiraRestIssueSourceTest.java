package vn.tsolve.ticket.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.ticket.entity.IssueComment;
import vn.tsolve.ticket.exception.JiraUnavailableException;
import vn.tsolve.ticket.service.JiraIssue;
import vn.tsolve.ticket.service.JiraIssuePage;

@ExtendWith(OutputCaptureExtension.class)
class JiraRestIssueSourceTest {

    private static final String EMAIL = "svc-jira@example.test";
    private static final String TOKEN = "test-token-value";
    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");

    private final WireMockServer jira = new WireMockServer(options().dynamicPort());

    @BeforeEach
    void start() {
        jira.start();
    }

    @AfterEach
    void stop() {
        jira.stop();
    }

    private JiraRestIssueSource source() {
        return new JiraRestIssueSource(
                jira.baseUrl() + "/", EMAIL, TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private static String issue(String key, String resolved, String assignee, String comments) {
        return "{\"key\":\"" + key + "\",\"fields\":{\"summary\":\"Summary of " + key + "\","
                + "\"description\":{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Description of " + key + "\"}]}]},"
                + "\"assignee\":" + (assignee == null ? "null" : "{\"displayName\":\"" + assignee + "\"}") + ","
                + "\"resolutiondate\":\"" + resolved + "\","
                + "\"comment\":" + comments + "}}";
    }

    private static String comment(String author, String created, String text) {
        return "{\"author\":{\"displayName\":\"" + author + "\"},\"created\":\"" + created + "\","
                + "\"body\":{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]}]}}";
    }

    private static final String NO_COMMENTS = "{\"comments\":[],\"total\":0,\"startAt\":0,\"maxResults\":5000}";

    private void searchReturns(String body) {
        jira.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql")).willReturn(okJson(body)));
    }

    @Test
    void searchesTheProjectsIssuesResolvedInTheRangeOldestFirst_AC_001_1() {
        searchReturns("{\"issues\":[" + issue("KAN-7", "2026-10-02T10:15:30.000+0700", "Hà", NO_COMMENTS) + "],"
                + "\"nextPageToken\":\"page-2\",\"isLast\":false}");

        JiraIssuePage page = source().resolvedIssues("KAN", FROM, TO, null);

        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam(
                        "jql",
                        equalTo("project = \"KAN\" AND resolved >= \"2026-10-01\" AND resolved < \"2026-11-01\""
                                + " ORDER BY resolved ASC, key ASC"))
                .withQueryParam("fields", equalTo("summary,description,assignee,resolutiondate,comment"))
                .withQueryParam("maxResults", equalTo("100"))
                .withHeader(
                        "Authorization",
                        equalTo("Basic "
                                + Base64.getEncoder()
                                        .encodeToString((EMAIL + ":" + TOKEN).getBytes(StandardCharsets.UTF_8)))));
        assertThat(page.issues()).extracting(JiraIssue::key).containsExactly("KAN-7");
        assertThat(page.nextPageToken()).isEqualTo("page-2");
    }

    @Test
    void asksForTheNextPageWithItsTokenAndReportsTheLastPage_AC_001_1() {
        searchReturns("{\"issues\":[],\"isLast\":true}");

        JiraIssuePage page = source().resolvedIssues("KAN", FROM, TO, "page-2");

        jira.verify(getRequestedFor(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("nextPageToken", equalTo("page-2")));
        assertThat(page.nextPageToken()).isNull();
    }

    @Test
    void keepsOnlyIssuesResolvedWithinTheRangeInVietnamTime_AC_001_1() {
        searchReturns("{\"issues\":["
                + issue("KAN-1", "2026-09-30T23:59:00.000+0700", null, NO_COMMENTS) + ","
                + issue("KAN-2", "2026-10-01T00:10:00.000+0700", null, NO_COMMENTS) + ","
                + issue("KAN-3", "2026-10-31T16:30:00.000+0000", null, NO_COMMENTS) + "," // 23:30 in Vietnam
                + issue("KAN-4", "2026-10-31T17:30:00.000+0000", null, NO_COMMENTS) // 00:30 on 1 Nov in Vietnam
                + "],\"isLast\":true}");

        assertThat(source().resolvedIssues("KAN", FROM, TO, null).issues())
                .extracting(JiraIssue::key)
                .containsExactly("KAN-2", "KAN-3");
    }

    @Test
    void takesTheSnapshotOfEachIssue_AC_001_2() {
        searchReturns("{\"issues\":["
                + issue(
                        "KAN-7",
                        "2026-10-02T10:15:30.000+0700",
                        "Hà",
                        "{\"comments\":[" + comment("Lan", "2026-10-02T09:00:00.000+0700", "Restarted the gateway.")
                                + "],\"total\":1,\"startAt\":0,\"maxResults\":5000}")
                + "," + issue("KAN-8", "2026-10-03T10:00:00.000+0700", null, NO_COMMENTS) + "],\"isLast\":true}");

        List<JiraIssue> issues = source().resolvedIssues("KAN", FROM, TO, null).issues();

        JiraIssue first = issues.get(0);
        assertThat(first.summary()).isEqualTo("Summary of KAN-7");
        assertThat(first.description()).isEqualTo("Description of KAN-7");
        assertThat(first.assignee()).isEqualTo("Hà");
        assertThat(first.resolvedAt()).isEqualTo(Instant.parse("2026-10-02T03:15:30Z"));
        assertThat(first.url()).isEqualTo(jira.baseUrl() + "/browse/KAN-7");
        assertThat(first.comments())
                .containsExactly(
                        new IssueComment("Lan", Instant.parse("2026-10-02T02:00:00Z"), "Restarted the gateway."));
        assertThat(issues.get(1).assignee()).isNull();
        assertThat(issues.get(1).comments()).isEmpty();
    }

    @Test
    void readsTheCommentsTheSearchLeftOut_AC_001_2() {
        searchReturns("{\"issues\":["
                + issue(
                        "KAN-7",
                        "2026-10-02T10:15:30.000+0700",
                        "Hà",
                        "{\"comments\":[" + comment("Lan", "2026-10-02T09:00:00.000+0700", "one")
                                + "],\"total\":3,\"startAt\":0,\"maxResults\":1}")
                + "],\"isLast\":true}");
        jira.stubFor(get(urlPathEqualTo("/rest/api/3/issue/KAN-7/comment"))
                .withQueryParam("startAt", equalTo("0"))
                .willReturn(okJson("{\"comments\":[" + comment("Lan", "2026-10-02T09:00:00.000+0700", "one") + ","
                        + comment("Minh", "2026-10-02T09:30:00.000+0700", "two")
                        + "],\"total\":3,\"startAt\":0,\"maxResults\":2}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/3/issue/KAN-7/comment"))
                .withQueryParam("startAt", equalTo("2"))
                .willReturn(okJson("{\"comments\":[" + comment("Hà", "2026-10-02T10:00:00.000+0700", "three")
                        + "],\"total\":3,\"startAt\":2,\"maxResults\":2}")));

        List<IssueComment> comments =
                source().resolvedIssues("KAN", FROM, TO, null).issues().get(0).comments();

        assertThat(comments).extracting(IssueComment::body).containsExactly("one", "two", "three");
    }

    @Test
    void reportsAJiraErrorWithItsStatusOnlyAndNeverTheCredentials_AC_001_7(CapturedOutput output) {
        jira.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .willReturn(aResponse().withStatus(401).withBody("{\"errorMessages\":[\"bad " + TOKEN + "\"]}")));

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "Jira answered HTTP 401")
                .hasNoCause();
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain(EMAIL);
    }

    @Test
    void reportsAServerErrorABrokenAnswerAndAClosedConnection_AC_001_7() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "Jira answered HTTP 503");

        jira.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql")).willReturn(okJson("{not json")));
        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null))
                .isInstanceOf(JiraUnavailableException.class);

        jira.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null))
                .isInstanceOf(JiraUnavailableException.class)
                .extracting("reason", STRING)
                .startsWith("Jira did not answer");
    }

    @Test
    void givesUpWhenJiraIsSlowerThanTheReadTimeout_AC_001_7() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .willReturn(okJson("{\"issues\":[],\"isLast\":true}").withFixedDelay(1500)));
        JiraRestIssueSource slow =
                new JiraRestIssueSource(jira.baseUrl(), EMAIL, TOKEN, Duration.ofSeconds(1), Duration.ofMillis(300));

        assertThatThrownBy(() -> slow.resolvedIssues("KAN", FROM, TO, null))
                .isInstanceOf(JiraUnavailableException.class)
                .extracting("reason", STRING)
                .startsWith("Jira did not answer");
    }

    @Test
    void reportsAMissingConnectionWithoutCallingJira_AC_001_7() {
        JiraRestIssueSource unset = new JiraRestIssueSource("", "", "", Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThatThrownBy(() -> unset.resolvedIssues("KAN", FROM, TO, null))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "The Jira connection is not configured");
        assertThat(jira.getAllServeEvents()).isEmpty();
    }

    @Test
    void refusesAProjectKeyThatCouldChangeTheQuery_AC_001_7() {
        assertThatThrownBy(() -> source().resolvedIssues("KAN\" OR project = \"OPS", FROM, TO, null))
                .isInstanceOf(JiraUnavailableException.class);
        assertThat(jira.getAllServeEvents()).isEmpty();
    }

    @Test
    void refusesAPlainHttpSiteAtStartUp() {
        assertThatThrownBy(() -> new JiraRestIssueSource("http://example.atlassian.net", EMAIL, TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JIRA_SITE_URL");
        assertThat(new JiraRestIssueSource("", "", "")).isNotNull();
    }
}
