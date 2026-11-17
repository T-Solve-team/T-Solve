package vn.tsolve.chatbot.issue.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.tsolve.chatbot.exception.ChatbotJiraException;
import vn.tsolve.tracker.dto.JiraAccount;

@Component
public class JiraRestIssueCreator implements JiraIssueCreator {
    private static final Logger log = LoggerFactory.getLogger(JiraRestIssueCreator.class);
    private static final String GATEWAY = "https://api.atlassian.com/ex/jira/";
    private static final Pattern CLOUD_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Set<String> ISSUE_FIELDS = Set.of("summary", "description", "labels", "reporter");
    private final RestClient.Builder builder;
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraRestIssueCreator() {
        this(clientBuilder(Duration.ofSeconds(10), Duration.ofSeconds(10)));
    }

    JiraRestIssueCreator(RestClient.Builder builder) {
        this.builder = builder;
    }

    static RestClient.Builder clientBuilder(Duration connectTimeout, Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public CreatedIssue create(JiraAccount account, NewIssue issue) {
        String cloudId =
                account.cloudId() == null ? "" : account.cloudId().strip().toLowerCase(Locale.ROOT);
        if (!CLOUD_ID.matcher(cloudId).matches()) {
            throw failure(HttpStatus.BAD_GATEWAY, "MSG148");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", issue.projectKey()));
        fields.put("issuetype", Map.of("name", "Task"));
        fields.put("summary", issue.summary());
        if (issue.description() != null && !issue.description().isBlank()) {
            var paragraphs = issue.description()
                    .lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", line))))
                    .toList();
            fields.put("description", Map.of("type", "doc", "version", 1, "content", paragraphs));
        }
        fields.put("labels", List.of("tsolve-chatbot"));
        fields.put("reporter", Map.of("id", issue.reporterId()));
        try {
            var response = builder.clone()
                    .build()
                    .post()
                    .uri(GATEWAY + "{cloudId}/rest/api/3/issue", cloudId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.apiToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", fields))
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = response.getBody();
            if (response.getStatusCode().value() != 201
                    || body == null
                    || !body.path("key").isTextual()
                    || body.path("key").textValue().isBlank()) {
                log.warn(
                        "Jira issue refusal status={} errors={}",
                        response.getStatusCode().value(),
                        List.of());
                // J8 (accepted): an ambiguous success may leave an issue in Jira without a stored key.
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MSG149");
            }
            String key = body.path("key").textValue();
            return new CreatedIssue(key, account.siteUrl().replaceAll("/+$", "") + "/browse/" + key);
        } catch (RestClientResponseException refusal) {
            int status = refusal.getStatusCode().value();
            List<String> keys = errorKeys(refusal.getResponseBodyAsString());
            log.warn("Jira issue refusal status={} errors={}", status, keys);
            if (status >= 500) {
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MSG149");
            }
            String code = status == 400
                            && !keys.contains("reporter")
                            && keys.stream().anyMatch(key -> !ISSUE_FIELDS.contains(key))
                    ? "MSG157"
                    : "MSG148";
            throw failure(HttpStatus.BAD_GATEWAY, code);
        } catch (RestClientException unavailable) {
            // Provider exceptions may embed the token or request body; discard their messages and causes.
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MSG149");
        }
    }

    private List<String> errorKeys(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode errors = root == null ? null : root.get("errors");
            if (errors != null && errors.isObject()) {
                List<String> keys = new ArrayList<>();
                errors.fieldNames().forEachRemaining(keys::add);
                keys.sort(String::compareTo);
                return keys;
            }
        } catch (JsonProcessingException invalid) {
            // A malformed refusal still maps to MSG148 without logging its body.
        }
        return List.of();
    }

    private static ChatbotJiraException failure(HttpStatus status, String code) {
        return new ChatbotJiraException(status, code);
    }
}
