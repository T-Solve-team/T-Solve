package vn.tsolve.identity.signin;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/** Test helpers for reading an authorization request URL. */
final class AuthorizationUris {

    private AuthorizationUris() {}

    /** The query parameters with their values percent-decoded; a repeated name keeps every value. */
    static MultiValueMap<String, String> params(String uri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>(
                UriComponentsBuilder.fromUriString(uri).build().getQueryParams());
        params.replaceAll((name, values) -> values.stream()
                .map(value -> value == null ? "" : UriUtils.decode(value, UTF_8))
                .toList());
        return params;
    }

    static String one(String uri, String name) {
        List<String> values = params(uri).get(name);
        if (values == null || values.size() != 1) {
            throw new AssertionError("expected exactly one '" + name + "' in " + uri + " but got " + values);
        }
        return values.get(0);
    }

    /** base64url without padding of SHA-256, the PKCE S256 and the nonce transform. */
    static String sha256Base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(value));
    }

    static String sha256Hex(String value) {
        return java.util.HexFormat.of().formatHex(sha256(value));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
