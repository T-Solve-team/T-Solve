package vn.tsolve.ticket.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;
import vn.tsolve.ticket.entity.IssueComment;
import vn.tsolve.ticket.exception.JiraUnavailableException;
import vn.tsolve.ticket.service.JiraIssue;
import vn.tsolve.ticket.service.JiraIssuePage;

@ExtendWith(OutputCaptureExtension.class)
class JiraRestIssueSourceTest {

    private static final String SITE_URL = "https://example.atlassian.net";
    private static final String CLOUD_ID = "test-cloud-id";
    private static final String GATEWAY = "https://api.atlassian.com/ex/jira/" + CLOUD_ID;
    private static final String SEARCH = GATEWAY + "/rest/api/3/search/jql";
    private static final String COMMENTS = GATEWAY + "/rest/api/3/issue/KAN-7/comment";
    private static final String TOKEN = "test-token-value";
    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");

    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(60);

    private final List<Duration> sleeps = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private RestClient.Builder builder;
    private MockRestServiceServer jira;

    @BeforeEach
    void start() {
        builder = RestClient.builder();
        jira = MockRestServiceServer.bindTo(builder).build();
    }

    @AfterEach
    void stop() {
        jira.verify();
    }

    private JiraRestIssueSource source() {
        return new JiraRestIssueSource(SITE_URL + "/", CLOUD_ID, TOKEN, builder, sleeps::add, clock);
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
        searchRequest().andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private ResponseActions searchRequest() {
        return jira.expect(request -> assertThat(request.getURI().toString()).startsWith(SEARCH + "?"))
                .andExpect(header("Authorization", "Bearer " + TOKEN));
    }

    @Test
    void searchesTheProjectsIssuesResolvedInTheRangeOldestFirst_AC_001_1() {
        searchRequest()
                .andExpect(request -> {
                    String jql = UriComponentsBuilder.fromUri(request.getURI())
                            .build()
                            .getQueryParams()
                            .getFirst("jql");
                    assertThat(jql).isNotNull();
                    assertThat(URLDecoder.decode(jql, StandardCharsets.UTF_8))
                            .isEqualTo("project = \"KAN\" AND resolved >= \"2026-09-30\" AND resolved < \"2026-11-02\""
                                    + " ORDER BY resolved ASC, key ASC");
                })
                .andExpect(queryParam("fields", "summary,description,assignee,resolutiondate,comment"))
                .andExpect(queryParam("maxResults", "100"))
                .andRespond(withSuccess(
                        "{\"issues\":[" + issue("KAN-7", "2026-10-02T10:15:30.000+0700", "Hà", NO_COMMENTS)
                                + "],\"nextPageToken\":\"page-2\",\"isLast\":false}",
                        MediaType.APPLICATION_JSON));

        JiraIssuePage page = source().resolvedIssues("KAN", FROM, TO, null, DEADLINE);
        assertThat(page.issues()).extracting(JiraIssue::key).containsExactly("KAN-7");
        assertThat(page.nextPageToken()).isEqualTo("page-2");
    }

    @Test
    void asksForTheNextPageWithItsTokenAndReportsTheLastPage_AC_001_1() {
        searchRequest()
                .andExpect(queryParam("nextPageToken", "page-2"))
                .andRespond(withSuccess("{\"issues\":[],\"isLast\":true}", MediaType.APPLICATION_JSON));

        JiraIssuePage page = source().resolvedIssues("KAN", FROM, TO, "page-2", DEADLINE);

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

        assertThat(source().resolvedIssues("KAN", FROM, TO, null, DEADLINE).issues())
                .extracting(JiraIssue::key)
                .containsExactly("KAN-2", "KAN-3");
    }

    @Test
    void stopsWithMsg50WithoutCallingJiraOnceThePullTookMoreThan60Seconds_AC_001_13_BUG_189() {
        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, NOW.minusMillis(1)))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "The pull took more than 60 seconds");
    }

    @Test
    void doesNotWaitForARetryThatWouldEndAfterTheDeadline_AC_001_13_BUG_189() {
        searchRequest()
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "5"));

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, NOW.plusSeconds(3)))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "The pull took more than 60 seconds");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void answersMsg50ForADateItCannotRead_AC_001_7_BUG_191() {
        searchReturns("{\"issues\":[" + issue("KAN-1", "02/10/2026 10:15", null, NO_COMMENTS) + "],\"isLast\":true}");

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class);
    }

    /**
     * Jira reads JQL dates in the account's time zone. With a UTC account, an issue resolved at 02:00 +0700 on the
     * first day is 19:00 UTC the day before, so a JQL of exactly the range would never return it.
     */
    @Test
    void asksJiraForADayMoreOnEachSideSoNoBoundaryIssueIsMissed_AC_001_1_BUG_188() {
        searchRequest()
                .andExpect(request -> assertThat(URLDecoder.decode(
                                UriComponentsBuilder.fromUri(request.getURI())
                                        .build()
                                        .getQueryParams()
                                        .getFirst("jql"),
                                StandardCharsets.UTF_8))
                        .contains("resolved >= \"2026-09-30\"", "resolved < \"2026-11-02\""))
                .andRespond(withSuccess(
                        "{\"issues\":["
                                + issue("KAN-1", "2026-09-30T19:00:00.000+0000", null, NO_COMMENTS) + ","
                                + issue("KAN-2", "2026-10-31T18:00:00.000+0000", null, NO_COMMENTS)
                                + "],\"isLast\":true}",
                        MediaType.APPLICATION_JSON));

        assertThat(source().resolvedIssues("KAN", FROM, TO, null, DEADLINE).issues())
                .extracting(JiraIssue::key)
                .containsExactly("KAN-1");
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

        List<JiraIssue> issues = source().resolvedIssues("KAN", FROM, TO, null, DEADLINE).issues();

        JiraIssue first = issues.get(0);
        assertThat(first.summary()).isEqualTo("Summary of KAN-7");
        assertThat(first.description()).isEqualTo("Description of KAN-7");
        assertThat(first.assignee()).isEqualTo("Hà");
        assertThat(first.resolvedAt()).isEqualTo(Instant.parse("2026-10-02T03:15:30Z"));
        assertThat(first.url()).isEqualTo(SITE_URL + "/browse/KAN-7");
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
        jira.expect(requestTo(COMMENTS + "?startAt=0&maxResults=100"))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        "{\"comments\":[" + comment("Lan", "2026-10-02T09:00:00.000+0700", "one") + ","
                                + comment("Minh", "2026-10-02T09:30:00.000+0700", "two")
                                + "],\"total\":3,\"startAt\":0,\"maxResults\":2}",
                        MediaType.APPLICATION_JSON));
        jira.expect(requestTo(COMMENTS + "?startAt=2&maxResults=100"))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        "{\"comments\":[" + comment("Hà", "2026-10-02T10:00:00.000+0700", "three")
                                + "],\"total\":3,\"startAt\":2,\"maxResults\":2}",
                        MediaType.APPLICATION_JSON));

        List<IssueComment> comments =
                source().resolvedIssues("KAN", FROM, TO, null, DEADLINE).issues().get(0).comments();

        assertThat(comments).extracting(IssueComment::body).containsExactly("one", "two", "three");
    }

    @Test
    void AC_001_7_reportsAJiraErrorWithItsStatusOnlyAndNeverTheToken(CapturedOutput output) {
        searchRequest()
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"errorMessages\":[\"bad " + TOKEN + "\"]}"));

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "Jira answered HTTP 401")
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void reportsAServerErrorABrokenAnswerAndAClosedConnection_AC_001_7() {
        searchRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        searchRequest().andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));
        searchRequest().andRespond(withException(new IOException("Connection reset")));
        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "Jira answered HTTP 503");

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class);

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .extracting("reason", STRING)
                .startsWith("Jira did not answer");
    }

    @Test
    void givesUpWhenJiraIsSlowerThanTheReadTimeout_AC_001_7() {
        searchRequest().andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .extracting("reason", STRING)
                .startsWith("Jira did not answer");
    }

    @Test
    void AC_001_7_enforcesTheReadTimeoutAgainstASlowServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/api/3/search/jql", exchange -> {
            try (exchange) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                byte[] body = "{\"issues\":[],\"isLast\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        try {
            server.start();
            String localUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            RestClient.Builder realClient = JiraRestIssueSource.clientBuilder(
                            Duration.ofSeconds(1), Duration.ofMillis(300))
                    .uriBuilderFactory(new DefaultUriBuilderFactory(localUrl));
            JiraRestIssueSource slow = new JiraRestIssueSource(SITE_URL, CLOUD_ID, TOKEN, realClient, Thread::sleep, clock);

            assertThatThrownBy(() -> slow.resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                    .isInstanceOf(JiraUnavailableException.class)
                    .hasFieldOrPropertyWithValue("reason", "Jira did not answer (SocketTimeoutException)");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsAMissingConnectionWithoutCallingJira_AC_001_7() {
        JiraRestIssueSource unset = new JiraRestIssueSource("", "", "", builder, sleeps::add, clock);

        assertThatThrownBy(() -> unset.resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "The Jira connection is not configured");
    }

    @Test
    void refusesAProjectKeyThatCouldChangeTheQuery_AC_001_7() {
        assertThatThrownBy(() -> source().resolvedIssues("KAN\" OR project = \"OPS", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class);
    }

    @Test
    void AC_001_12_sendsBearerToTheGatewayAndBuildsBrowseLinksOnTheSite() {
        searchReturns("{\"issues\":[" + issue("KAN-7", "2026-10-02T10:15:30.000+0700", null, NO_COMMENTS)
                + "],\"isLast\":true}");

        assertThat(source().resolvedIssues("KAN", FROM, TO, null, DEADLINE).issues())
                .extracting(JiraIssue::url)
                .containsExactly(SITE_URL + "/browse/KAN-7");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void AC_001_11_retries429WithoutRetryAfterAfterOneSecond() {
        searchRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        searchReturns("{\"issues\":[],\"isLast\":true}");

        assertThat(source().resolvedIssues("KAN", FROM, TO, null, DEADLINE).issues()).isEmpty();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void AC_001_11_retries503WithRetryAfter() {
        searchRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "5"));
        searchReturns("{\"issues\":[],\"isLast\":true}");

        assertThat(source().resolvedIssues("KAN", FROM, TO, null, DEADLINE).issues()).isEmpty();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void AC_001_11_fails503WithoutRetryAfterAtOnce() {
        searchRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "Jira answered HTTP 503");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void AC_001_11_failsRetryAfterSixSecondsAtOnce() {
        searchRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "6"));

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "Jira answered HTTP 429");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void AC_001_11_failsAfterExactlyThree429Requests(CapturedOutput output) {
        for (int attempt = 0; attempt < 3; attempt++) {
            searchRequest()
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", "2")
                            .body(TOKEN));
        }

        assertThatThrownBy(() -> source().resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                .isInstanceOf(JiraUnavailableException.class)
                .hasFieldOrPropertyWithValue("reason", "Jira answered HTTP 429")
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void AC_001_11_retriesEachCommentPageIndependentlyOfSearch() {
        searchRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        searchRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        searchReturns("{\"issues\":["
                + issue("KAN-7", "2026-10-02T10:15:30.000+0700", null, "{\"comments\":[],\"total\":2}")
                + "],\"isLast\":true}");
        for (int offset = 0; offset < 2; offset++) {
            String url = COMMENTS + "?startAt=" + offset + "&maxResults=100";
            for (int attempt = 0; attempt < 2; attempt++) {
                jira.expect(requestTo(url))
                        .andExpect(header("Authorization", "Bearer " + TOKEN))
                        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "0"));
            }
            jira.expect(requestTo(url))
                    .andExpect(header("Authorization", "Bearer " + TOKEN))
                    .andRespond(withSuccess(
                            "{\"comments\":[" + comment("Lan", "2026-10-02T09:00:00.000+0700", "comment " + offset)
                                    + "],\"total\":2}",
                            MediaType.APPLICATION_JSON));
        }

        assertThat(source().resolvedIssues("KAN", FROM, TO, null, DEADLINE)
                        .issues()
                        .get(0)
                        .comments())
                .extracting(IssueComment::body)
                .containsExactly("comment 0", "comment 1");
        assertThat(sleeps)
                .containsExactly(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ZERO);
    }

    @Test
    void AC_001_7_requiresEveryConnectionSetting() {
        for (String[] settings :
                List.of(new String[] {" ", CLOUD_ID, TOKEN}, new String[] {SITE_URL, " ", TOKEN}, new String[] {
                    SITE_URL, CLOUD_ID, " "
                })) {
            JiraRestIssueSource unset =
                    new JiraRestIssueSource(settings[0], settings[1], settings[2], builder, sleeps::add, clock);

            assertThatThrownBy(() -> unset.resolvedIssues("KAN", FROM, TO, null, DEADLINE))
                    .isInstanceOf(JiraUnavailableException.class)
                    .hasFieldOrPropertyWithValue("reason", "The Jira connection is not configured");
        }
        assertThat(sleeps).isEmpty();
    }

    @Test
    void refusesAPlainHttpSiteAtStartUp() {
        assertThatThrownBy(() -> new JiraRestIssueSource("http://example.atlassian.net", CLOUD_ID, TOKEN, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JIRA_SITE_URL");
        assertThat(new JiraRestIssueSource("", "", "", clock)).isNotNull();
    }
}
