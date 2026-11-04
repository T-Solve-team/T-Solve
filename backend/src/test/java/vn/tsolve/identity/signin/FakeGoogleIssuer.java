package vn.tsolve.identity.signin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A fake Google for the sign-in tests: a WireMock server with a token endpoint and a JWKS endpoint, and ID tokens
 * signed with RSA keys made at run time (no key material is committed). One server per JVM; call {@link #reset()}
 * before each test.
 */
public final class FakeGoogleIssuer {

    public static final String KEY_ID = "fake-key-1";
    public static final String ISSUER = "https://accounts.google.com";
    public static final String CLIENT_ID = "test-client-id";
    public static final String CLIENT_SECRET = "test-secret";

    private static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());
    private static final RSAKey KEY;
    private static final RSAKey OTHER_KEY;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            OTHER_KEY = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        SERVER.start();
        stubJwks();
    }

    private FakeGoogleIssuer() {}

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }

    public static String tokenUri() {
        return baseUrl() + "/token";
    }

    public static String jwksUri() {
        return baseUrl() + "/jwks";
    }

    public static void reset() {
        SERVER.resetAll();
        stubJwks();
    }

    private static void stubJwks() {
        SERVER.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JWKSet(KEY.toPublicJWK()).toString())));
    }

    // ---- ID tokens

    /** Claims of a good token at {@link MutableClock#START}; {@code edit} may change or remove any of them. */
    public static Map<String, Object> goodClaims(Instant now, String nonce) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", CLIENT_ID);
        claims.put("sub", "google-sub-1");
        claims.put("email", "lan.tran@acme.example");
        claims.put("email_verified", Boolean.TRUE);
        claims.put("hd", "acme.example");
        claims.put("name", "Lan Tran");
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(3600).getEpochSecond());
        if (nonce != null) {
            claims.put("nonce", nonce);
        }
        return claims;
    }

    private static JWTClaimsSet claims(Instant now, String nonce, Consumer<Map<String, Object>> edit) {
        Map<String, Object> claims = goodClaims(now, nonce);
        edit.accept(claims);
        try {
            return JWTClaimsSet.parse(claims);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String idToken(String nonce, Consumer<Map<String, Object>> edit) {
        return idToken(MutableClock.START, nonce, edit);
    }

    public static String idToken(Instant now, String nonce, Consumer<Map<String, Object>> edit) {
        return sign(KEY, KEY_ID, claims(now, nonce, edit));
    }

    public static String signedWithOtherKey(String nonce, Consumer<Map<String, Object>> edit) {
        return sign(OTHER_KEY, KEY_ID, claims(MutableClock.START, nonce, edit));
    }

    public static String withUnknownKid(String nonce, Consumer<Map<String, Object>> edit) {
        return sign(KEY, "kid-that-is-not-published", claims(MutableClock.START, nonce, edit));
    }

    /** {@code alg: none}. */
    public static String unsigned(String nonce, Consumer<Map<String, Object>> edit) {
        return new PlainJWT(claims(MutableClock.START, nonce, edit)).serialize();
    }

    /** HS256 with a key made from {@code secret} (padded to the 32 bytes HS256 needs). */
    public static String hs256(String secret, String nonce, Consumer<Map<String, Object>> edit) {
        byte[] key = Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8), 32);
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims(MutableClock.START, nonce, edit));
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(RSAKey key, String kid, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(kid)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- token endpoint stubs

    private static String tokenBody(String idToken) {
        return "{\"access_token\":\"fake-access\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"id_token\":\""
                + idToken + "\"}";
    }

    public static void answerToken(String idToken) {
        answerTokenBody(tokenBody(idToken));
    }

    public static void answerTokenStatus(int status) {
        SERVER.stubFor(post(urlEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    public static void answerTokenBody(String body) {
        SERVER.stubFor(post(urlEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    public static void delayToken(String idToken, int milliseconds) {
        SERVER.stubFor(post(urlEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(milliseconds)
                        .withBody(tokenBody(idToken))));
    }

    public static void faultToken(Fault fault) {
        SERVER.stubFor(post(urlEqualTo("/token")).willReturn(aResponse().withFault(fault)));
    }

    public static void jwksDown() {
        SERVER.stubFor(get(urlEqualTo("/jwks")).willReturn(aResponse().withStatus(500)));
    }

    public static void delayJwks(int milliseconds) {
        SERVER.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(milliseconds)
                        .withBody(new JWKSet(KEY.toPublicJWK()).toString())));
    }

    public static void jwksReset() {
        SERVER.stubFor(get(urlEqualTo("/jwks")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    // ---- what the app sent

    /** The form parameters of each request the token endpoint got. */
    public static List<Map<String, String>> tokenRequests() {
        List<Map<String, String>> requests = new ArrayList<>();
        for (LoggedRequest request : SERVER.findAll(
                com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/token")))) {
            Map<String, String> form = new LinkedHashMap<>();
            for (String pair : request.getBodyAsString().split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String name = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                form.put(
                        URLDecoder.decode(name, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
            requests.add(form);
        }
        return requests;
    }

    public static int jwksRequestCount() {
        return SERVER.findAll(getRequestedFor(urlEqualTo("/jwks"))).size();
    }
}
