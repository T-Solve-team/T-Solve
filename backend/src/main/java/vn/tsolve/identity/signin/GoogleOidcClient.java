package vn.tsolve.identity.signin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to Google's OIDC endpoints: builds the authorization URL and exchanges the code for the ID token. It makes
 * its own HTTP client, so the class needs no other bean.
 */
@Component
public class GoogleOidcClient {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleOidcClient.class);

    static final String CALLBACK_PATH = "/api/v1/auth/callback";

    private final SignInProperties properties;
    private final String authorizationUri;
    private final String tokenUri;
    private final RestClient http;

    public GoogleOidcClient(
            SignInProperties properties,
            @Value("${tsolve.oidc.google.authorization-uri}") String authorizationUri,
            @Value("${tsolve.oidc.google.token-uri}") String tokenUri,
            @Value("${tsolve.oidc.google.connect-timeout}") Duration connectTimeout,
            @Value("${tsolve.oidc.google.read-timeout}") Duration readTimeout) {
        this.properties = properties;
        this.authorizationUri = authorizationUri;
        this.tokenUri = tokenUri;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** The {@code id_token} for the code, or empty on a non-2xx answer, a timeout or a missing token. */
    public Optional<String> exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("client_id", properties.googleClientId());
        form.add("client_secret", properties.googleClientSecret());
        form.add("redirect_uri", redirectUri());
        try {
            Map<?, ?> answer = http.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object idToken = answer == null ? null : answer.get("id_token");
            if (idToken instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
            LOG.warn("Google token exchange failed: the answer had no id_token");
            return Optional.empty();
        } catch (RestClientResponseException e) {
            // the status only: the message and the body can carry the code or Google's error detail
            LOG.warn(
                    "Google token exchange failed: HTTP status {}",
                    e.getStatusCode().value());
            return Optional.empty();
        } catch (RuntimeException e) {
            // the class name only: the message could carry the code
            LOG.warn("Google token exchange failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * The URL that sends the browser to Google: the code flow with scope, the state value, the nonce and a PKCE
     * {@code S256} challenge, and the callback on the central sign-in origin as the redirect URI (AC-001.4/.5).
     * {@code hostedDomain} becomes {@code hd}; it is {@code null} for the Platform Console.
     */
    public URI authorizationUrl(String stateValue, SignInState state, String hostedDomain) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.googleClientId())
                .queryParam("scope", "openid email profile")
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", stateValue)
                .queryParam("nonce", state.getNonce())
                .queryParam("code_challenge", s256(state.getPkceVerifier()))
                .queryParam("code_challenge_method", "S256");
        if (hostedDomain != null) {
            url.queryParam("hd", hostedDomain);
        }
        return url.build().encode().toUri();
    }

    /** The PKCE S256 challenge: base64url (no padding) of the SHA-256 of the verifier (RFC 7636). */
    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Always the callback on the configured central sign-in origin, never a value from the request. */
    String redirectUri() {
        return UriComponentsBuilder.fromUri(properties.loginOrigin())
                .replacePath(CALLBACK_PATH)
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
    }
}
