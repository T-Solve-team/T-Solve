package vn.tsolve.tracker.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
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
        return created == null ? "" : created.path("id").asText("");
    }

    @Override
    public Optional<String> findComment(JiraAccount account, String issueKey, String textPrefix) {
        RestClient http = http(account);
        JsonNode me = call(() -> http.get().uri("/rest/api/3/myself").retrieve().body(JsonNode.class));
        String accountId = me == null ? "" : me.path("accountId").asText("");
        JsonNode page = call(() -> http.get().uri(COMMENTS, issueKey).retrieve().body(JsonNode.class));
        if (page == null || accountId.isEmpty()) {
            return Optional.empty();
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
