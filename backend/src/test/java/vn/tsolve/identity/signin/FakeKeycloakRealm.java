package vn.tsolve.identity.signin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Test-only: an in-process stand-in for the Keycloak realm {@code tsolve}, for the tests that need the token side
 * without a Keycloak container. It serves discovery (with the issuer the application checks at start-up), the token
 * endpoint (one stub per authorization code), the JWKS and userinfo, signs ID tokens with an RSA key made at run time,
 * and records every token request. The login origin of the application under test is {@link #origin()}.
 */
public final class FakeKeycloakRealm {

    public static final String KEY_ID = "fake-keycloak-key";
    public static final String CLIENT_ID = "tsolve-web";
    public static final String SUB = "kc-user-alice";
    public static final String EMAIL = "alice@acme.test";
    public static final String ORGANIZATION_ID = "00000000-0000-4000-8000-0000000000c1";

    private static final String REALM_PATH = "/realms/tsolve";
    private static final String OIDC = REALM_PATH + "/protocol/openid-connect";
    private static final String TOKEN = OIDC + "/token";

    private final WireMockServer server = new WireMockServer(options().dynamicPort());
    private final RSAKey key;
    private final RSAKey otherKey;

    public FakeKeycloakRealm() {
        try {
            key = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            otherKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public void start() {
        server.start();
        reset();
    }

    public void stop() {
        server.stop();
    }

    /** {@code TSOLVE_LOGIN_ORIGIN} of the application under test (localhost: Java resolves it everywhere). */
    public String origin() {
        return "http://localhost:" + server.port();
    }

    public String issuer() {
        return origin() + REALM_PATH;
    }

    /** Forgets every stub and every recorded request, then publishes discovery, the keys and userinfo again. */
    public void reset() {
        server.resetAll();
        String oidc = origin() + OIDC;
        server.stubFor(get(urlPathEqualTo(REALM_PATH + "/.well-known/openid-configuration"))
                .willReturn(json(
                        200,
                        "{\"issuer\":\"" + issuer() + "\",\"authorization_endpoint\":\"" + oidc
                                + "/auth\",\"token_endpoint\":\"" + oidc + "/token\",\"jwks_uri\":\"" + oidc
                                + "/certs\",\"userinfo_endpoint\":\"" + oidc
                                + "/userinfo\",\"subject_types_supported\":[\"public\"],"
                                + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}")));
        server.stubFor(
                get(urlPathEqualTo(OIDC + "/certs")).willReturn(json(200, new JWKSet(key.toPublicJWK()).toString())));
        server.stubFor(get(urlPathEqualTo(OIDC + "/userinfo"))
                .willReturn(json(200, "{\"sub\":\"" + SUB + "\",\"email\":\"" + EMAIL + "\"}")));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    // ---- ID tokens

    /** The claims of a good ID token for alice of acme; {@code nonce} is the {@code nonce} parameter of the start. */
    public Map<String, Object> goodClaims(String nonce) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer());
        claims.put("aud", CLIENT_ID);
        claims.put("sub", SUB);
        claims.put("email", EMAIL);
        claims.put("email_verified", Boolean.TRUE);
        claims.put("organization", Map.of("acme", Map.of("id", ORGANIZATION_ID)));
        claims.put("hd", "acme.test");
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(300).getEpochSecond());
        if (nonce != null) {
            claims.put("nonce", nonce);
        }
        return claims;
    }

    public String idToken(String nonce, Consumer<Map<String, Object>> edit) {
        return sign(key, nonce, edit);
    }

    /** Signed with a key the JWKS does not publish (same key id). */
    public String signedWithOtherKey(String nonce, Consumer<Map<String, Object>> edit) {
        return sign(otherKey, nonce, edit);
    }

    private String sign(RSAKey signingKey, String nonce, Consumer<Map<String, Object>> edit) {
        Map<String, Object> claims = goodClaims(nonce);
        edit.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(KEY_ID)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    JWTClaimsSet.parse(claims));
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException | ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- token endpoint, one stub per authorization code

    /** The token endpoint answers this ID token to a request that carries this code. */
    public void answerCode(String code, String idToken) {
        server.stubFor(post(urlPathEqualTo(TOKEN))
                .withRequestBody(containing("code=" + code))
                .willReturn(json(
                        200,
                        "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\",\"expires_in\":300,"
                                + "\"id_token\":\"" + idToken + "\"}")));
    }

    public void answerCodeStatus(String code, int status, String body) {
        server.stubFor(post(urlPathEqualTo(TOKEN))
                .withRequestBody(containing("code=" + code))
                .willReturn(json(status, body)));
    }

    public void answerCodeFault(String code, Fault fault) {
        server.stubFor(post(urlPathEqualTo(TOKEN))
                .withRequestBody(containing("code=" + code))
                .willReturn(aResponse().withFault(fault)));
    }

    // ---- what the application sent

    /** The form parameters of each request the token endpoint got, oldest first. */
    public List<Map<String, String>> tokenRequests() {
        List<Map<String, String>> requests = new ArrayList<>();
        for (LoggedRequest request : server.findAll(postRequestedFor(urlPathEqualTo(TOKEN)))) {
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
}
