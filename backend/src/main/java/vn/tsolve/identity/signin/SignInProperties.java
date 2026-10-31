package vn.tsolve.identity.signin;

import java.net.URI;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sign-in settings bound from {@code tsolve.auth}, which {@code application.yml} fills from the environment with no
 * default (NFR 4). A missing or invalid value stops the start-up, and the message names the environment value. The
 * signing key is never put in a message or in {@link #toString()}.
 */
@ConfigurationProperties("tsolve.auth")
public record SignInProperties(
        URI loginOrigin,
        URI adminOrigin,
        String signingKey,
        String googleClientId,
        String googleClientSecret,
        String operatorEmails) {

    /** HMAC-SHA256 needs a key of at least as many bytes as the hash is long. */
    static final int MIN_SIGNING_KEY_BYTES = 32;

    public SignInProperties {
        requireOrigin(loginOrigin, "TSOLVE_LOGIN_ORIGIN");
        requireOrigin(adminOrigin, "TSOLVE_ADMIN_ORIGIN");
        requireSigningKey(signingKey);
        requireText(googleClientId, "GOOGLE_CLIENT_ID");
        requireText(googleClientSecret, "GOOGLE_CLIENT_SECRET");
        requireText(operatorEmails, "PLATFORM_OPERATOR_EMAILS");
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

    private static void requireSigningKey(String signingKey) {
        String envName = "TSOLVE_SIGNING_KEY";
        requireText(signingKey, envName);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(signingKey);
        } catch (IllegalArgumentException e) {
            // not chained: the cause could quote the key
            throw new IllegalArgumentException(envName + " must be base64");
        }
        if (decoded.length < MIN_SIGNING_KEY_BYTES) {
            throw new IllegalArgumentException(envName + " must be at least " + MIN_SIGNING_KEY_BYTES + " bytes");
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
