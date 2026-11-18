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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.tsolve.tracker.dto.JiraAccount;

/** {@code GET /rest/api/3/user?accountId=} through the Atlassian gateway with the app token (scope read:jira-user). */
@Component
public class RestJiraUserClient implements JiraUserClient {

    private static final Logger log = LoggerFactory.getLogger(RestJiraUserClient.class);
    private static final String GATEWAY = "https://api.atlassian.com/ex/jira/";
    private static final Pattern CLOUD_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final RestClient.Builder builder;

    public RestJiraUserClient() {
        this(clientBuilder(Duration.ofSeconds(10)));
    }

    RestJiraUserClient(RestClient.Builder builder) {
        this.builder = builder;
    }

    static RestClient.Builder clientBuilder(Duration timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public Optional<String> displayName(JiraAccount account, String accountId) {
        String cloudId =
                account.cloudId() == null ? "" : account.cloudId().strip().toLowerCase(Locale.ROOT);
        if (!CLOUD_ID.matcher(cloudId).matches() || accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode user = builder.clone()
                    .build()
                    .get()
                    .uri(GATEWAY + "{cloudId}/rest/api/3/user?accountId={accountId}", cloudId, accountId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.apiToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode name = user == null ? null : user.get("displayName");
            return name != null && name.isTextual() && !name.textValue().isBlank()
                    ? Optional.of(name.textValue().strip())
                    : Optional.empty();
        } catch (RestClientResponseException refusal) {
            log.warn("Jira user read refused status={}", refusal.getStatusCode().value());
            return Optional.empty();
        } catch (RestClientException unavailable) {
            // The provider message may hold the token or the URL: log the type only.
            log.warn("Jira user read failed ({})", unavailable.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
