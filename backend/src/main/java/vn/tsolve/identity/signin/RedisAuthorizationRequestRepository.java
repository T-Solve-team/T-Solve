package vn.tsolve.identity.signin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Keeps one Keycloak authorization request in Redis (AC-001.53): a JSON string under
 * {@code tsolve:authreq:<SHA-256 of the cookie value>} for 10 minutes, read and deleted in one {@code GETDEL}. The
 * browser holds only a random value in {@code __Host-TSOLVE_AUTHREQ}; the state, nonce and verifier never reach it.
 */
@Component
public class RedisAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "__Host-TSOLVE_AUTHREQ";
    public static final String KEY_PREFIX = "tsolve:authreq:";

    private static final Logger log = LoggerFactory.getLogger(RedisAuthorizationRequestRepository.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public RedisAuthorizationRequestRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** What is stored: enough to rebuild the Spring request, attributes included. */
    record Stored(
            String authorizationUri,
            String clientId,
            String redirectUri,
            List<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes,
            String authorizationRequestUri) {}

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return read(cookieValue(request), false);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Stored stored = new Stored(
                authorizationRequest.getAuthorizationUri(),
                authorizationRequest.getClientId(),
                authorizationRequest.getRedirectUri(),
                List.copyOf(authorizationRequest.getScopes()),
                authorizationRequest.getState(),
                authorizationRequest.getAdditionalParameters(),
                authorizationRequest.getAttributes(),
                authorizationRequest.getAuthorizationRequestUri());
        try {
            redis.opsForValue().set(key(value), json.writeValueAsString(stored), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot store the authorization request", e);
        }
        // written by hand: ResponseCookie would add an Expires attribute
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                COOKIE_NAME + "=" + value + "; Path=/; Max-Age=" + TTL.toSeconds()
                        + "; Secure; HttpOnly; SameSite=Lax");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest saved = read(cookieValue(request), true);
        if (saved != null && saved.getAttribute(KeycloakAuthorizationRequestResolver.RETURN_TO) instanceof String to) {
            // hands the kept return_to to OidcCallbackHandler, which runs after Spring has consumed the request
            request.setAttribute(OidcCallbackHandler.RETURN_TO_ATTRIBUTE, to);
        }
        return saved;
    }

    private OAuth2AuthorizationRequest read(String value, boolean consume) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String key = key(value);
        String stored = consume
                ? redis.opsForValue().getAndDelete(key)
                : redis.opsForValue().get(key);
        if (stored == null) {
            return null;
        }
        try {
            Stored s = json.readValue(stored, Stored.class);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(s.authorizationUri())
                    .clientId(s.clientId())
                    .redirectUri(s.redirectUri())
                    .scopes(new LinkedHashSet<>(s.scopes()))
                    .state(s.state())
                    .additionalParameters(s.additionalParameters())
                    .attributes(s.attributes())
                    .authorizationRequestUri(s.authorizationRequestUri())
                    .build();
        } catch (JsonProcessingException e) {
            log.warn("Discarded an unreadable stored authorization request");
            return null;
        }
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private static String key(String cookieValue) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cookieValue.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
