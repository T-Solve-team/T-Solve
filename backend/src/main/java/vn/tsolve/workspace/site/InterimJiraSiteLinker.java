package vn.tsolve.workspace.site;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * [Interim] until platform Task 18 jira-site-link. Reads the public cloudId only: persists nothing and cannot
 * detect a site linked elsewhere; the catalog will own that check and storage.
 */
@Component
public class InterimJiraSiteLinker implements JiraSiteLinker {

    private static final Logger log = LoggerFactory.getLogger(InterimJiraSiteLinker.class);

    private final RestClient http;

    @Autowired
    public InterimJiraSiteLinker(
            @Value("${tsolve.tracker.jira.connect-timeout:5s}") Duration connectTimeout,
            @Value("${tsolve.tracker.jira.read-timeout:10s}") Duration readTimeout) {
        this(clientBuilder(connectTimeout, readTimeout));
    }

    /** The production client: a redirect could escape the validated Jira host, so none is followed (AC-009.4). */
    static RestClient.Builder clientBuilder(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    InterimJiraSiteLinker(RestClient.Builder builder) {
        this.http = builder.build();
    }

    @Override
    public String linkJiraSite(String normalisedSiteUrl) {
        if (JiraSiteRule.normalise(normalisedSiteUrl)
                .filter(site -> site.equals(normalisedSiteUrl))
                .isEmpty()) {
            throw new JiraSiteLinkRefusedException(JiraSiteLinkRefusedException.Reason.UNREADABLE);
        }
        String host = URI.create(normalisedSiteUrl).getHost();
        try {
            ResponseEntity<JsonNode> response = http.get()
                    .uri(URI.create(normalisedSiteUrl + "/_edge/tenant_info"))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status.value() != 200, (request, answer) -> {
                        throw unreadable(host, answer.getStatusCode().value());
                    })
                    .toEntity(JsonNode.class);
            JsonNode body = response.getBody();
            String cloudId = body == null ? "" : body.path("cloudId").asText("");
            if (cloudId.isBlank()) {
                throw unreadable(host, response.getStatusCode().value());
            }
            return cloudId;
        } catch (RestClientException e) {
            // No response (0), or a response that cannot be decoded. Never retain the body or cause.
            throw unreadable(host, 0);
        }
    }

    private static JiraSiteLinkRefusedException unreadable(String host, int status) {
        log.warn("Jira tenant information unreadable: host={}, status={}", host, status);
        return new JiraSiteLinkRefusedException(JiraSiteLinkRefusedException.Reason.UNREADABLE);
    }
}
