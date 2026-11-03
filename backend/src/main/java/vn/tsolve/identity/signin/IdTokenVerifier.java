package vn.tsolve.identity.signin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.support.NoOpCache;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Verifies a Google ID token (AC-001.9): RS256 signature against Google's JWKS, issuer, audience (our client id),
 * expiry with a 60 s skew, the state's nonce, a verified email. Empty on any failure; never throws and never logs
 * the token.
 */
@Component
public class IdTokenVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(IdTokenVerifier.class);
    private static final Duration SKEW = Duration.ofSeconds(60);

    private final NimbusJwtDecoder decoder;
    private final String clientId;
    private final List<String> issuers;
    private final IdentityClock clock;

    public IdTokenVerifier(
            SignInProperties properties,
            @Value("${tsolve.oidc.google.jwk-set-uri}") String jwkSetUri,
            @Value("${tsolve.oidc.google.issuer}") String issuer,
            @Value("${tsolve.oidc.google.connect-timeout}") Duration connectTimeout,
            @Value("${tsolve.oidc.google.read-timeout}") Duration readTimeout,
            IdentityClock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // shortcut: no JWKS cache, one fetch per sign-in (Spring's default cache is shared per URI and would
                // hide a JWKS outage); add a short per-instance cache if sign-in volume makes the fetch a cost
                .cache(new NoOpCache("google-jwks"))
                .restOperations(new RestTemplate(factory))
                .build();
        // the signature is checked by the decoder; the claims below, so the time comes from the IdentityClock
        this.decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        this.clientId = properties.googleClientId();
        // Google signs "https://accounts.google.com" or "accounts.google.com"
        this.issuers = List.of(issuer, issuer.replaceFirst("^https://", ""));
        this.clock = clock;
    }

    public Optional<GoogleIdentity> verify(String idToken, String expectedNonce) {
        try {
            Jwt jwt = decoder.decode(idToken);
            if (!claimsAreValid(jwt, expectedNonce)) {
                LOG.warn("ID token verification failed: a claim was rejected");
                return Optional.empty();
            }
            return Optional.of(new GoogleIdentity(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("hd"),
                    jwt.getClaimAsString("name")));
        } catch (RuntimeException e) {
            // the class name only: the message can carry the token or the JWKS answer
            LOG.warn("ID token verification failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean claimsAreValid(Jwt jwt, String expectedNonce) {
        Instant now = clock.now();
        Instant expiresAt = jwt.getExpiresAt();
        String email = jwt.getClaimAsString("email");
        return issuers.contains(jwt.getClaimAsString("iss"))
                && jwt.getAudience() != null
                && jwt.getAudience().contains(clientId)
                && expiresAt != null
                && !now.minus(SKEW).isAfter(expiresAt)
                && expectedNonce != null
                && expectedNonce.equals(jwt.getClaimAsString("nonce"))
                && Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))
                && email != null
                && !email.isBlank();
    }
}
