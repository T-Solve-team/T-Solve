package vn.tsolve.ticket.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.tsolve.ticket.entity.IssueComment;
import vn.tsolve.ticket.exception.JiraUnavailableException;
import vn.tsolve.ticket.service.JiraIssue;
import vn.tsolve.ticket.service.JiraIssuePage;
import vn.tsolve.ticket.service.JiraIssueSource;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.service.JiraServiceAccount;

/**
 * {@link JiraIssueSource} over the Jira Cloud REST API v3 with the Workspace's service account (read from
 * {@code workspace} once per pull, KAN-345, BUG-311): its scoped API token sent as Bearer through the Atlassian gateway. No
 * account answers MSG50 (AC-001.7, AC-001.14). Logs and errors carry only the project key, an HTTP status or an error type (NFR 2, 4).
 */
@Component
public class JiraRestIssueSource implements JiraIssueSource {

    private static final Logger log = LoggerFactory.getLogger(JiraRestIssueSource.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    // Jira writes 2026-10-02T10:15:30.000+0700 (no colon in the offset)
    private static final DateTimeFormatter JIRA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    // Jira project keys: an upper-case letter, then letters, digits or _. Anything else could change the JQL.
    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final String FIELDS = "summary,description,assignee,resolutiondate,comment";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_RETRIES = 2;
    private static final int MAX_RETRY_SECONDS = 5;

    private static final String GATEWAY = "https://api.atlassian.com/ex/jira/";
    // The cloud id becomes part of the gateway path: only a canonical UUID may (workspace AC-011.8).
    private static final Pattern CLOUD_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final JiraServiceAccount accounts;
    private final RestClient.Builder builder;
    private final Sleeper sleeper;
    private final Clock clock;

    /** The Workspace's account is read once per pull (AC-001.12, KAN-345); nothing comes from the environment. */
    @Autowired
    public JiraRestIssueSource(JiraServiceAccount accounts, Clock clock) {
        this(accounts, clientBuilder(Duration.ofSeconds(10), Duration.ofSeconds(30)), Thread::sleep, clock);
    }

    JiraRestIssueSource(JiraServiceAccount accounts, RestClient.Builder builder, Sleeper sleeper, Clock clock) {
        this.accounts = accounts;
        this.builder = builder;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    /** One pull's connection: the gateway client with the token and the site for the issue links. */
    private record Connection(RestClient client, String siteUrl) {}

    // AC-001.14: no account, or one that is incomplete or unusable, is a connection that is not configured (MSG50)
    private Connection connect() {
        JiraAccount account = accounts.current().orElse(null);
        String site = account == null || account.siteUrl() == null
                ? ""
                : account.siteUrl().strip().replaceAll("/+$", "");
        String cloudId = account == null || account.cloudId() == null
                ? ""
                : account.cloudId().strip().toLowerCase(Locale.ROOT);
        if (!site.startsWith("https://") || !CLOUD_ID.matcher(cloudId).matches() || isBlank(account.apiToken())) {
            throw new JiraUnavailableException("The Jira connection is not configured");
        }
        RestClient client = builder.clone()
                .baseUrl(GATEWAY + cloudId)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + account.apiToken())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        return new Connection(client, site);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static RestClient.Builder clientBuilder(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public JiraIssuePage resolvedIssues(
            String projectKey, LocalDate from, LocalDate to, String pageToken, Instant deadline) {
        return pull(projectKey, from, to, deadline).page(pageToken);
    }

    /** Reads the account once; every page of the pull uses it (AC-001.12, BUG-311). */
    @Override
    public Pull pull(String projectKey, LocalDate from, LocalDate to, Instant deadline) {
        if (projectKey == null || !PROJECT_KEY.matcher(projectKey).matches()) {
            throw new JiraUnavailableException("The Project's Jira key is not valid");
        }
        Connection jira = connect();
        return pageToken -> page(jira, projectKey, from, to, pageToken, deadline);
    }

    private JiraIssuePage page(
            Connection jira, String projectKey, LocalDate from, LocalDate to, String pageToken, Instant deadline) {
        // JQL dates follow the account's profile time zone, at most 19 h away from Vietnam time: ask for a day more on
        // each side and keep only the Vietnam-time range below (AC-001.1, BUG-188)
        String jql = "project = \"" + projectKey + "\" AND resolved >= \"" + from.minusDays(1) + "\" AND resolved < \""
                + to.plusDays(2) + "\" ORDER BY resolved ASC, key ASC";
        JsonNode page = call(projectKey, deadline, () -> jira.client()
                .get()
                .uri(uri -> {
                    uri.path("/rest/api/3/search/jql")
                            .queryParam("jql", "{jql}")
                            .queryParam("fields", FIELDS)
                            .queryParam("maxResults", PAGE_SIZE);
                    if (pageToken != null) {
                        uri.queryParam("nextPageToken", "{token}");
                    }
                    return pageToken == null ? uri.build(jql) : uri.build(jql, pageToken);
                })
                .retrieve()
                .body(JsonNode.class));

        List<JiraIssue> issues = new ArrayList<>();
        for (JsonNode issue : page.path("issues")) {
            JiraIssue read = issue(jira, projectKey, issue, deadline);
            LocalDate resolvedDay = read.resolvedAt().atZone(VIETNAM).toLocalDate();
            if (!resolvedDay.isBefore(from) && !resolvedDay.isAfter(to)) {
                issues.add(read);
            }
        }
        boolean last = page.path("isLast").asBoolean(true) || !page.hasNonNull("nextPageToken");
        return new JiraIssuePage(
                issues, last ? null : page.path("nextPageToken").asText());
    }

    private JiraIssue issue(Connection jira, String projectKey, JsonNode issue, Instant deadline) {
        String key = issue.path("key").asText();
        JsonNode fields = issue.path("fields");
        JsonNode assignee = fields.path("assignee");
        return new JiraIssue(
                key,
                fields.path("summary").asText(""),
                AdfText.of(fields.get("description")),
                assignee.hasNonNull("displayName") ? assignee.get("displayName").asText() : null,
                time(fields.path("resolutiondate").asText(null)),
                jira.siteUrl() + "/browse/" + key,
                comments(jira, projectKey, key, fields.path("comment"), deadline));
    }

    /** The comments the search returned, and the rest from the comment endpoint when the search left some out. */
    private List<IssueComment> comments(
            Connection jira, String projectKey, String issueKey, JsonNode fromSearch, Instant deadline) {
        JsonNode inSearch = fromSearch.path("comments");
        if (fromSearch.path("total").asInt(inSearch.size()) <= inSearch.size()) {
            return readComments(inSearch);
        }
        List<IssueComment> all = new ArrayList<>();
        int startAt = 0;
        while (true) {
            int offset = startAt;
            JsonNode page = call(projectKey, deadline, () -> jira.client()
                    .get()
                    .uri(uri -> uri.path("/rest/api/3/issue/{key}/comment")
                            .queryParam("startAt", offset)
                            .queryParam("maxResults", PAGE_SIZE)
                            .build(issueKey))
                    .retrieve()
                    .body(JsonNode.class));
            List<IssueComment> read = readComments(page.path("comments"));
            all.addAll(read);
            startAt += read.size();
            if (read.isEmpty() || startAt >= page.path("total").asInt(0)) {
                return all;
            }
        }
    }

    private static List<IssueComment> readComments(JsonNode comments) {
        List<IssueComment> read = new ArrayList<>();
        for (JsonNode comment : comments) {
            JsonNode author = comment.path("author");
            String body = AdfText.of(comment.get("body"));
            read.add(new IssueComment(
                    author.hasNonNull("displayName") ? author.get("displayName").asText() : null,
                    time(comment.path("created").asText(null)),
                    body == null ? "" : body));
        }
        return read;
    }

    private static Instant time(String jiraTime) {
        if (jiraTime == null) {
            throw new JiraUnavailableException("Jira answered without a date");
        }
        try {
            return OffsetDateTime.parse(jiraTime, JIRA_TIME).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(jiraTime).toInstant();
            } catch (DateTimeParseException unreadable) {
                // MSG50, not a 500 (AC-001.7, BUG-191)
                throw new JiraUnavailableException("Jira answered a date that cannot be read");
            }
        }
    }

    private interface Call {
        JsonNode run();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration delay) throws InterruptedException;
    }

    /**
     * One Jira request; every failure becomes MSG50 with a reason that names no credential or content. Neither the
     * request nor a retry wait starts past the pull's deadline (AC-001.13).
     */
    // ponytail: a request already running when the deadline passes still ends at its 30 s read timeout, so a pull
    // can answer up to ~40 s late; a per-request timeout cut to the time left would close that gap
    private JsonNode call(String projectKey, Instant deadline, Call call) {
        for (int retries = 0; ; retries++) {
            requireBefore(projectKey, deadline, clock.instant());
            try {
                JsonNode body = call.run();
                if (body == null || !body.isObject()) {
                    throw new JiraUnavailableException("Jira answered without a usable body");
                }
                return body;
            } catch (RestClientResponseException e) {
                long seconds = retrySeconds(e);
                if (retries < MAX_RETRIES && seconds >= 0 && seconds <= MAX_RETRY_SECONDS) {
                    requireBefore(projectKey, deadline, clock.instant().plusSeconds(seconds));
                    try {
                        sleeper.sleep(Duration.ofSeconds(seconds));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new JiraUnavailableException("Jira did not answer (InterruptedException)");
                    }
                    continue;
                }
                log.warn(
                        "Jira import of project {}: Jira answered HTTP {}",
                        projectKey,
                        e.getStatusCode().value());
                throw new JiraUnavailableException(
                        "Jira answered HTTP " + e.getStatusCode().value());
            } catch (RestClientException | DateTimeParseException e) {
                String type = rootType(e);
                log.warn("Jira import of project {}: Jira did not answer ({})", projectKey, type);
                throw new JiraUnavailableException("Jira did not answer (" + type + ")");
            }
        }
    }

    private static void requireBefore(String projectKey, Instant deadline, Instant at) {
        if (at.isAfter(deadline)) {
            log.warn("Jira import of project {}: the pull took more than 60 seconds", projectKey);
            throw new JiraUnavailableException("The pull took more than 60 seconds");
        }
    }

    private static long retrySeconds(RestClientResponseException error) {
        int status = error.getStatusCode().value();
        if (status != 429 && status != 503) {
            return -1;
        }
        HttpHeaders headers = error.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return status == 429 ? 1 : -1;
        }
        try {
            return Long.parseLong(retryAfter.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String rootType(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
