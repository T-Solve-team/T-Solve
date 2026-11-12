package vn.tsolve.identity.signin;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sign-in settings bound from {@code tsolve.auth}, which {@code application.yml} fills from the environment with no
 * default (NFR 4). A missing or invalid value stops the start-up, and the message names the environment value.
 */
@ConfigurationProperties("tsolve.auth")
public record SignInProperties(
        URI loginOrigin, URI adminOrigin, String operatorEmails, String keycloakWebClientSecret) {

    public SignInProperties {
        requireOrigin(loginOrigin, "TSOLVE_LOGIN_ORIGIN");
        requireOrigin(adminOrigin, "TSOLVE_ADMIN_ORIGIN");
        requireText(operatorEmails, "PLATFORM_OPERATOR_EMAILS");
        requireText(keycloakWebClientSecret, "KEYCLOAK_WEB_CLIENT_SECRET");
    }

    private static void requireOrigin(URI origin, String envName) {
        boolean usable = origin != null
                && origin.getHost() != null
                && ("http".equalsIgnoreCase(origin.getScheme()) || "https".equalsIgnoreCase(origin.getScheme()));
        if (!usable) {
            throw new IllegalArgumentException(
                    envName + " must be set to an origin with a scheme, such as" + " https://login.example.com");
        }
    }

    private static void requireText(String value, String envName) {
        // Spring Boot leaves an unset ${NAME} in the text instead of failing, so it counts as missing
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalArgumentException(envName + " must be set");
        }
    }

    @Override
    public String toString() {
        return "SignInProperties[loginOrigin=" + loginOrigin + ", adminOrigin=" + adminOrigin + "]";
    }
}
