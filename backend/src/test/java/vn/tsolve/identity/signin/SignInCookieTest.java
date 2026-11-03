package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class SignInCookieTest {

    private final SignInCookie cookie = new SignInCookie();

    private static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("AC-001.44 makes a 32-byte base64url value without padding")
    void makesA32ByteBase64urlValue_AC_001_44() {
        String value = cookie.newValue();

        assertThat(value).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(Base64.getUrlDecoder().decode(value)).hasSize(32);
    }

    @Test
    @DisplayName("AC-001.44 makes a different value every time")
    void makesADifferentValueEveryTime_AC_001_44() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            values.add(cookie.newValue());
        }

        assertThat(values).hasSize(1000);
    }

    @Test
    @DisplayName("AC-001.44 hashes the value with SHA-256")
    void hashesTheValueWithSha256_AC_001_44() throws Exception {
        String value = "abcDEF-_123";

        assertThat(cookie.hash(value)).hasSize(32).isEqualTo(sha256(value)).isEqualTo(cookie.hash(value));
    }

    @Test
    @DisplayName("AC-001.44 issues the cookie with the __Host- attributes and a 600 s life")
    void issuesTheCookieWithHostAttributes_AC_001_44() {
        ResponseCookie issued = cookie.issue("some-value");

        String header = issued.toString();
        assertThat(issued.getName()).isEqualTo("__Host-TSOLVE_SIGNIN");
        assertThat(issued.getValue()).isEqualTo("some-value");
        assertThat(header).contains("__Host-TSOLVE_SIGNIN=some-value");
        assertThat(SignInTestFixtures.attributes(header))
                .containsEntry("max-age", "600")
                .containsEntry("path", "/")
                .containsKeys("secure", "httponly")
                .doesNotContainKey("domain");
        assertThat(SignInTestFixtures.attributes(header).get("samesite")).isEqualToIgnoringCase("Lax");
    }

    @Test
    @DisplayName("AC-001.47 expires the cookie with Max-Age 0 and the same attributes")
    void expiresTheCookie_AC_001_47() {
        ResponseCookie expired = cookie.expire();

        String header = expired.toString();
        assertThat(expired.getName()).isEqualTo("__Host-TSOLVE_SIGNIN");
        assertThat(expired.getValue()).isEmpty();
        assertThat(SignInTestFixtures.attributes(header))
                .containsEntry("max-age", "0")
                .containsEntry("path", "/")
                .containsKeys("secure", "httponly")
                .doesNotContainKey("domain");
        assertThat(SignInTestFixtures.attributes(header).get("samesite")).isEqualToIgnoringCase("Lax");
    }

    @Test
    @DisplayName("AC-001.46 matches only the value whose hash is stored")
    void matchesOnlyTheValueWhoseHashIsStored_AC_001_46() throws Exception {
        String value = cookie.newValue();
        byte[] stored = cookie.hash(value);

        assertThat(cookie.matches(value, stored)).isTrue();
        assertThat(cookie.matches(cookie.newValue(), stored)).isFalse();
        assertThat(cookie.matches(null, stored)).isFalse();
        assertThat(cookie.matches("", stored)).isFalse();
        assertThat(cookie.matches(value, null)).isFalse();
        assertThat(cookie.matches(value, new byte[16])).isFalse();
        assertThat(cookie.matches(value, sha256("other"))).isFalse();
    }

    @Test
    @DisplayName("AC-001.46 / edge never fails on odd input")
    void neverFailsOnOddInput_AC_001_46() {
        byte[] stored = new byte[32];

        assertThat(cookie.matches("x".repeat(10 * 1024), stored)).isFalse();
        assertThat(cookie.matches("café漢字", stored)).isFalse();
        assertThat(cookie.matches("%00", stored)).isFalse();
    }
}
