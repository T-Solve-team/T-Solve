package vn.tsolve.fakegoogle;

import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import wiremock.com.github.jknack.handlebars.Helper;

/**
 * Template helpers for the fake Google. The RSA key is made at start-up and lives only in memory.
 *
 * <ul>
 *   <li>{@code {{idtoken iss=.. aud=.. sub=.. email=.. hd=.. body=..}}} signs a fresh RS256 ID token; the nonce is
 *       the part of {@code code} after the first dot (the authorize stub put it there).
 *   <li>{@code {{jwk}}} is the matching public key as one JWK, for the {@code /jwks} stub.
 * </ul>
 */
public class IdTokenExtension implements TemplateHelperProviderExtension {
  // random per start, like the key: Keycloak's cached JWKS can never match a new key by a stale id
  private static final String KID = UUID.randomUUID().toString();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final KeyPair KEY = newKey();

  private static KeyPair newKey() {
    try {
      KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
      g.initialize(2048);
      return g.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String getName() {
    return "fake-google-id-token";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    Helper<Object> idtoken =
        (ctx, options) -> {
          String code = formParam(options.hash("body"), "code");
          String nonce = code.substring(code.indexOf('.') + 1);
          long now = Instant.now().getEpochSecond();
          String payload =
              String.format(
                  "{\"iss\":%s,\"aud\":%s,\"sub\":%s,\"email\":%s,\"email_verified\":true,\"hd\":%s,\"nonce\":%s,"
                      + "\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}",
                  q(options.hash("iss")),
                  q(options.hash("aud")),
                  q(options.hash("sub")),
                  q(options.hash("email")),
                  q(options.hash("hd")),
                  q(nonce),
                  now,
                  now + 3600,
                  UUID.randomUUID());
          String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + KID + "\"}";
          String input = b64(header) + "." + b64(payload);
          try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(KEY.getPrivate());
            s.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + B64.encodeToString(s.sign());
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        };
    Helper<Object> jwk =
        (ctx, options) -> {
          RSAPublicKey k = (RSAPublicKey) KEY.getPublic();
          return String.format(
              "{\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"kid\":\"%s\",\"n\":\"%s\",\"e\":\"%s\"}",
              KID, unsigned(k.getModulus().toByteArray()), unsigned(k.getPublicExponent().toByteArray()));
        };
    return Map.of("idtoken", idtoken, "jwk", jwk);
  }

  /** One value out of a form-encoded request body. */
  private static String formParam(Object body, String name) {
    for (String pair : String.valueOf(body).split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv[0].equals(name) && kv.length == 2) {
        return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
      }
    }
    throw new IllegalArgumentException("no " + name + " in the token request");
  }

  private static String b64(String s) {
    return B64.encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String q(Object v) {
    return "\"" + String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /** BigInteger.toByteArray adds a leading zero byte for a positive number whose top bit is set. */
  private static String unsigned(byte[] b) {
    int skip = b.length > 1 && b[0] == 0 ? 1 : 0;
    return B64.encodeToString(java.util.Arrays.copyOfRange(b, skip, b.length));
  }
}
