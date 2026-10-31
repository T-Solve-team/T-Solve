package vn.tsolve.identity.signin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to Google's OIDC endpoints. For now it only builds the authorization URL; the code exchange comes with the
 * callback. No HTTP client is created here, so the class needs no other bean.
 */
@Component
public class GoogleOidcClient {

    static final String CALLBACK_PATH = "/api/v1/auth/callback";

    private final SignInProperties properties;
    private final String authorizationUri;

    public GoogleOidcClient(
            SignInProperties properties, @Value("${tsolve.oidc.google.authorization-uri}") String authorizationUri) {
        this.properties = properties;
        this.authorizationUri = authorizationUri;
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
