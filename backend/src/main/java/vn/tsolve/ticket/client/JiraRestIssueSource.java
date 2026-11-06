package vn.tsolve.ticket.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * {@link JiraIssueSource} over the Jira Cloud REST API v3 with the Workspace service account (email + API token, basic
 * auth). The settings may be empty — the app starts and a pull answers MSG50 (AC-001.7) — but a site URL that is set
 * must be https. Logs and errors carry only the project key, an HTTP status or an error type (NFR 2, 4).
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

    private final String siteUrl;
    private final RestClient client;

    @Autowired
    public JiraRestIssueSource(
            @Value("${JIRA_SITE_URL:}") String siteUrl,
            @Value("${JIRA_EMAIL:}") String email,
            @Value("${JIRA_API_TOKEN:}") String apiToken) {
        this(httpsOnly(siteUrl), email, apiToken, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    JiraRestIssueSource(String siteUrl, String email, String apiToken, Duration connectTimeout, Duration readTimeout) {
        String site = siteUrl == null ? "" : siteUrl.strip().replaceAll("/+$", "");
        boolean configured = !site.isEmpty() && !isBlank(email) && !isBlank(apiToken);
        this.siteUrl = configured ? site : null;
        this.client = configured ? client(site, email, apiToken, connectTimeout, readTimeout) : null;
    }

    private static String httpsOnly(String siteUrl) {
        if (!isBlank(siteUrl) && !siteUrl.strip().startsWith("https://")) {
            throw new IllegalStateException("JIRA_SITE_URL must start with https://");
        }
        return siteUrl;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static RestClient client(
            String site, String email, String apiToken, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        String basic = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
                .baseUrl(site)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    @Override
    public JiraIssuePage resolvedIssues(String projectKey, LocalDate from, LocalDate to, String pageToken) {
        if (client == null) {
            throw new JiraUnavailableException("The Jira connection is not configured");
        }
        if (projectKey == null || !PROJECT_KEY.matcher(projectKey).matches()) {
            throw new JiraUnavailableException("The Project's Jira key is not valid");
        }
        String jql = "project = \"" + projectKey + "\" AND resolved >= \"" + from + "\" AND resolved < \""
                + to.plusDays(1) + "\" ORDER BY resolved ASC, key ASC";
        JsonNode page = call(projectKey, () -> client.get()
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
            JiraIssue read = issue(projectKey, issue);
            LocalDate resolvedDay = read.resolvedAt().atZone(VIETNAM).toLocalDate();
            // JQL dates follow the account's profile time zone; the range is in Vietnam time (AC-001.1)
            if (!resolvedDay.isBefore(from) && !resolvedDay.isAfter(to)) {
                issues.add(read);
            }
        }
        boolean last = page.path("isLast").asBoolean(true) || !page.hasNonNull("nextPageToken");
        return new JiraIssuePage(
                issues, last ? null : page.path("nextPageToken").asText());
    }

    private JiraIssue issue(String projectKey, JsonNode issue) {
        String key = issue.path("key").asText();
        JsonNode fields = issue.path("fields");
        JsonNode assignee = fields.path("assignee");
        return new JiraIssue(
                key,
                fields.path("summary").asText(""),
                AdfText.of(fields.get("description")),
                assignee.hasNonNull("displayName") ? assignee.get("displayName").asText() : null,
                time(fields.path("resolutiondate").asText(null)),
                siteUrl + "/browse/" + key,
                comments(projectKey, key, fields.path("comment")));
    }

    /** The comments the search returned, and the rest from the comment endpoint when the search left some out. */
    private List<IssueComment> comments(String projectKey, String issueKey, JsonNode fromSearch) {
        JsonNode inSearch = fromSearch.path("comments");
        if (fromSearch.path("total").asInt(inSearch.size()) <= inSearch.size()) {
            return readComments(inSearch);
        }
        List<IssueComment> all = new ArrayList<>();
        int startAt = 0;
        while (true) {
            int offset = startAt;
            JsonNode page = call(projectKey, () -> client.get()
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
            return OffsetDateTime.parse(jiraTime).toInstant();
        }
    }

    private interface Call {
        JsonNode run();
    }

    /** One Jira request; every failure becomes MSG50 with a reason that names no credential or content. */
    private static JsonNode call(String projectKey, Call call) {
        try {
            JsonNode body = call.run();
            if (body == null || !body.isObject()) {
                throw new JiraUnavailableException("Jira answered without a usable body");
            }
            return body;
        } catch (RestClientResponseException e) {
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

    private static String rootType(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
