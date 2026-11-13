package vn.tsolve.identity.signin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.identity.signin.port.LocalTenantScope;
import vn.tsolve.identity.signin.port.TenantScope;

/** Binds {@link SignInProperties}; the application does not start while one of its values is missing. */
@Configuration
@EnableConfigurationProperties(SignInProperties.class)
public class SignInConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SignInConfig.class);
    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Start-up check, once: Keycloak's discovery issuer must equal {@code TSOLVE_LOGIN_ORIGIN/realms/tsolve}. An
     * unreachable Keycloak or a 5xx only warns (it may still be booting); a wrong issuer or a reply that is not a
     * discovery document stops the start.
     */
    @Bean
    public InitializingBean keycloakDiscoveryCheck(SignInProperties properties) {
        return () -> {
            checkDiscovery(properties.loginOrigin().toString(), "tsolve");
            checkDiscovery(properties.loginOrigin().toString(), "tsolve-ops");
        };
    }

    private static void checkDiscovery(String origin, String realm) {
        String base = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        String expectedIssuer = base + "/realms/" + realm;
        String discoveryUrl = expectedIssuer + "/.well-known/openid-configuration";
        HttpResponse<String> response;
        try {
            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(DISCOVERY_TIMEOUT).build();
            response = client.send(
                    HttpRequest.newBuilder(URI.create(discoveryUrl))
                            .timeout(DISCOVERY_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            LOG.warn("Keycloak is not reachable; could not check its issuer {}: {}", expectedIssuer, e.toString());
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking " + discoveryUrl);
        }
        if (response.statusCode() >= 500) {
            LOG.warn("Keycloak answered {}; could not check its issuer {}", response.statusCode(), expectedIssuer);
            return;
        }
        JsonNode issuer = null;
        if (response.statusCode() == 200) {
            try {
                issuer = new ObjectMapper().readTree(response.body()).path("issuer");
            } catch (IOException e) {
                // not JSON: reported below
            }
        }
        if (issuer == null || !issuer.isTextual()) {
            throw new IllegalStateException("The discovery document " + discoveryUrl + " is missing or not valid (HTTP "
                    + response.statusCode() + ")");
        }
        if (!expectedIssuer.equals(issuer.asText())) {
            throw new IllegalStateException("Keycloak's issuer is " + issuer.asText() + " but " + expectedIssuer
                    + " is expected; set TSOLVE_LOGIN_ORIGIN to match Keycloak's hostname");
        }
    }

    @Bean
    public IdentityClock identityClock() {
        return new IdentityClock(Clock.systemUTC());
    }

    @Bean
    public TenantScope tenantScope() {
        return new LocalTenantScope();
    }
}
