package vn.tsolve.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Signs test FITs with a local RSA key, shaped like Forge's (issuer, audience = app id, context.cloudId). */
final class FitTokens {

    static final String APP_ID = "ari:cloud:ecosystem::app/test-app";
    static final String CLOUD_ID = "cloud-registered";

    static final RSAKey KEY = generate("atlassian-key");
    static final RSAKey OTHER_KEY = generate("attacker-key");

    private FitTokens() {}

    static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(FitValidators.ISSUER)
                .audience(List.of(APP_ID))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("app", Map.of("id", APP_ID, "apiBaseUrl", "https://api.atlassian.com/ex/jira/" + CLOUD_ID))
                .claim("context", Map.of("cloudId", CLOUD_ID));
    }

    static String sign(UnaryOperator<JWTClaimsSet.Builder> claims) {
        return sign(KEY, claims);
    }

    static String sign(RSAKey key, UnaryOperator<JWTClaimsSet.Builder> claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .build(),
                    claims.apply(validClaims()).build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** HS256 "signed" with the public key's bytes: the classic algorithm-confusion attack. */
    static String signHs256WithPublicKey() {
        try {
            byte[] secret = KEY.toRSAPublicKey().getEncoded();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256), validClaims().build());
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
