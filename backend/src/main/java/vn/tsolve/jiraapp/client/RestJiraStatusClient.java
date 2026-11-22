package vn.tsolve.jiraapp.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.tsolve.tracker.dto.JiraAccount;

/** {@code GET /rest/api/3/status/{id}} through the Atlassian gateway with the app token (scope read:jira-work). */
@Component
public class RestJiraStatusClient implements JiraStatusClient {

    private static final Logger log = LoggerFactory.getLogger(RestJiraStatusClient.class);
    private static final String GATEWAY = "https://api.atlassian.com/ex/jira/";
    private static final Pattern CLOUD_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern STATUS_ID = Pattern.compile("[0-9]{1,18}");

    private final RestClient.Builder builder;

    public RestJiraStatusClient() {
        this(RestJiraUserClient.clientBuilder(Duration.ofSeconds(10)));
    }

    RestJiraStatusClient(RestClient.Builder builder) {
        this.builder = builder;
    }

    @Override
    public Optional<String> categoryOf(JiraAccount account, String statusId) {
        String cloudId =
                account.cloudId() == null ? "" : account.cloudId().strip().toLowerCase(Locale.ROOT);
        if (!CLOUD_ID.matcher(cloudId).matches()
                || statusId == null
                || !STATUS_ID.matcher(statusId).matches()) {
            return Optional.empty();
        }
        try {
            JsonNode status = builder.clone()
                    .build()
                    .get()
                    .uri(GATEWAY + "{cloudId}/rest/api/3/status/{id}", cloudId, statusId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.apiToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode key = status == null ? null : status.path("statusCategory").get("key");
            return key != null && key.isTextual() && !key.textValue().isBlank()
                    ? Optional.of(key.textValue().strip())
                    : Optional.empty();
        } catch (RestClientResponseException refusal) {
            log.warn(
                    "Jira status read refused status={}",
                    refusal.getStatusCode().value());
            return Optional.empty();
        } catch (RestClientException unavailable) {
            // The provider message may hold the token or the URL: log the type only.
            log.warn("Jira status read failed ({})", unavailable.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
