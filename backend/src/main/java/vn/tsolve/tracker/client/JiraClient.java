package vn.tsolve.tracker.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;

/**
 * Jira Cloud REST API v3 as the Workspace's service account (Basic auth: email + API token). Errors keep only the HTTP
 * status: Jira's body may quote the issue, and the credentials never leave this class (Non-Functional 4, 9).
 */
@Component
public class JiraClient implements TicketTrackerClient {

    // Jira lists comments oldest first by default; the newest page is enough to find the one T-Solve posted.
    private static final String COMMENTS = "/rest/api/3/issue/{issueKey}/comment?orderBy=-created&maxResults=100";

    private final RestClient.Builder builder;

    @Autowired
    public JiraClient(
            @Value("${tsolve.tracker.jira.connect-timeout:5s}") Duration connectTimeout,
            @Value("${tsolve.tracker.jira.read-timeout:10s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.builder = RestClient.builder().requestFactory(factory);
    }

    JiraClient(RestClient.Builder builder) {
        this.builder = builder;
    }

    @Override
    public String addComment(JiraAccount account, String issueKey, List<String> paragraphs) {
        Map<String, Object> body = Map.of("body", adf(paragraphs));
        JsonNode created = call(() -> http(account)
                .post()
                .uri("/rest/api/3/issue/{issueKey}/comment", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        // A success without an id would mark the result as commented with nothing to point at (BUG-167).
        return requiredText(created, "id");
    }

    @Override
    public Optional<String> findComment(JiraAccount account, String issueKey, String textPrefix) {
        RestClient http = http(account);
        JsonNode me = call(() -> http.get().uri("/rest/api/3/myself").retrieve().body(JsonNode.class));
        // Without the account id no comment can be recognised as ours, and "not found" would post a second one
        // (BUG-168).
        String accountId = requiredText(me, "accountId");
        JsonNode page = call(() -> http.get().uri(COMMENTS, issueKey).retrieve().body(JsonNode.class));
        if (page == null) {
            throw new JiraUnavailableException(0);
        }
        for (JsonNode comment : page.path("comments")) {
            boolean ours =
                    accountId.equals(comment.path("author").path("accountId").asText());
            if (ours && AdfTextExtractor.toText(comment.path("body")).startsWith(textPrefix)) {
                return Optional.of(comment.path("id").asText());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> findProject(JiraAccount account, String projectKey) {
        try {
            JsonNode project = call(() -> http(account)
                    .get()
                    .uri("/rest/api/3/project/{projectKey}", projectKey)
                    .retrieve()
                    .body(JsonNode.class));
            return Optional.of(requiredString(project, "key"));
        } catch (JiraRejectedException e) {
            if (e.getStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public List<TrackerStatus> projectStatuses(JiraAccount account, String projectKey) {
        JsonNode issueTypes = call(() -> http(account)
                .get()
                .uri("/rest/api/3/project/{projectKey}/statuses", projectKey)
                .retrieve()
                .body(JsonNode.class));
        if (issueTypes == null || !issueTypes.isArray()) {
            throw new JiraUnavailableException(0);
        }
        List<TrackerStatus> statuses = new ArrayList<>();
        for (JsonNode issueType : issueTypes) {
            JsonNode issueStatuses = issueType.path("statuses");
            if (!issueStatuses.isArray()) {
                throw new JiraUnavailableException(0);
            }
            for (JsonNode status : issueStatuses) {
                String id = requiredString(status, "id");
                String name = requiredString(status, "name");
                boolean done =
                        "done".equals(status.path("statusCategory").path("key").asText());
                statuses.add(new TrackerStatus(id, name, done));
            }
        }
        return List.copyOf(statuses);
    }

    private static String requiredString(JsonNode node, String field) {
        if (node == null || !node.path(field).isTextual()) {
            throw new JiraUnavailableException(0);
        }
        return requiredText(node, field);
    }

    // An answer Jira should never give: treated as unavailable so the job retries rather than acting on it.
    private static String requiredText(JsonNode node, String field) {
        String value = node == null ? "" : node.path(field).asText("");
        if (value.isBlank()) {
            throw new JiraUnavailableException(0);
        }
        return value;
    }

    private RestClient http(JiraAccount account) {
        return builder.clone()
                .baseUrl(account.siteUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(account.email(), account.apiToken()))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429 || status >= 500) {
                throw new JiraUnavailableException(status);
            }
            throw new JiraRejectedException(status);
        } catch (RestClientException e) {
            // No answer: connection refused, timeout, unreadable body.
            throw new JiraUnavailableException(0);
        }
    }

    private static Map<String, Object> adf(List<String> paragraphs) {
        List<Map<String, Object>> content = paragraphs.stream()
                .map(text -> Map.<String, Object>of(
                        "type", "paragraph", "content", List.of(Map.of("type", "text", "text", text))))
                .toList();
        return Map.of("type", "doc", "version", 1, "content", content);
    }
}
