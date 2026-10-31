package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** SignedToken is shared with T10's hand-off codes, so its rules are pinned here once, without Spring. */
class SignedTokenTest {

    private final SignedToken token = new SignedToken(SignInTestFixtures.properties());

    @Test
    @DisplayName("AC-001.2 signs with HMAC-SHA256 of the server key and verifies its own token")
    void signsWithHmacSha256AndVerifiesItsOwnToken_AC_001_2() throws Exception {
        String signed = token.sign("abc");

        assertThat(signed).startsWith("abc.");
        String mac = signed.substring("abc.".length());
        assertThat(mac).matches("[A-Za-z0-9_-]+=*");
        assertThat(Base64.getUrlDecoder().decode(mac)).isEqualTo(hmacSha256(SignInTestFixtures.KEY, "abc"));
        assertThat(token.verify(signed)).contains("abc");
    }

    @Test
    @DisplayName("edge 20 rejects a token whose payload was edited")
    void rejectsEditedPayload_edge20() {
        String signed = token.sign("abc");

        assertThat(token.verify("abd" + signed.substring(3))).isEmpty();
    }

    @Test
    @DisplayName("edge 20 rejects a token whose signature was edited")
    void rejectsEditedSignature_edge20() {
        String signed = token.sign("abc");
        int macStart = signed.indexOf('.') + 1;
        // the first MAC character carries six full bits, so changing it always changes the MAC
        char first = signed.charAt(macStart);
        String edited = signed.substring(0, macStart) + (first == 'A' ? 'B' : 'A') + signed.substring(macStart + 1);

        assertThat(token.verify(edited)).isEmpty();
    }

    @Test
    @DisplayName("edge 20 rejects an unsigned value")
    void rejectsUnsignedValue_edge20() {
        assertThat(token.verify(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    @DisplayName("edge 20 rejects a token signed with another key")
    void rejectsTokenSignedWithAnotherKey_edge20() {
        String foreign = new SignedToken(SignInTestFixtures.properties(SignInTestFixtures.OTHER_KEY)).sign("abc");

        assertThat(token.verify(foreign)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "abc.", ".mac", "a.b.c", "abc.%%%"})
    @DisplayName("edge 20 rejects malformed values without throwing")
    void rejectsMalformedValuesWithoutThrowing_edge20(String value) {
        AtomicReference<Optional<String>> result = new AtomicReference<>();

        assertThatCode(() -> result.set(token.verify(value))).doesNotThrowAnyException();
        assertThat(result.get()).isEmpty();
    }

    private static byte[] hmacSha256(String base64Key, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64Key), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }
}
