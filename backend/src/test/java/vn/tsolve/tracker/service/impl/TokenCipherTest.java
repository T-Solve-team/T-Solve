package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.config.TrackerProperties;
import vn.tsolve.tracker.exception.TokenUnreadableException;

class TokenCipherTest {

    private static final String TOKEN = "system-token-value";

    private final TokenCipher cipher = new TokenCipher(properties(randomKey(32)));

    @Test
    void decryptsWhatItEncrypted_AC_003_1() {
        assertThat(cipher.decrypt(cipher.encrypt(TOKEN))).isEqualTo(TOKEN);
    }

    @Test
    void neverStoresTokenInClear_AC_003_1() {
        byte[] stored = cipher.encrypt(TOKEN);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(TOKEN);
    }

    @Test
    void usesFreshIvForEveryToken_AC_003_1() {
        assertThat(cipher.encrypt(TOKEN)).isNotEqualTo(cipher.encrypt(TOKEN));
    }

    @Test
    void rejectsDamagedCiphertext_AC_004_6() {
        byte[] stored = cipher.encrypt(TOKEN);
        stored[stored.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(TokenUnreadableException.class);
    }

    @Test
    void rejectsTokenEncryptedUnderOldKey_AC_004_6() {
        byte[] stored = new TokenCipher(properties(randomKey(32))).encrypt(TOKEN);
        assertThatThrownBy(() -> cipher.decrypt(stored))
                .isInstanceOf(TokenUnreadableException.class)
                .hasMessage(TokenUnreadableException.ERROR_TYPE);
    }

    @Test
    void rejectsTooShortCiphertext_AC_004_6() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[5])).isInstanceOf(TokenUnreadableException.class);
    }

    @Test
    void refusesKeyOfWrongLength_AC_003_1() {
        assertThatThrownBy(() -> new TokenCipher(properties(randomKey(16))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void refusesKeyThatIsNotBase64_AC_003_1() {
        assertThatThrownBy(() -> new TokenCipher(properties("not base64 !!")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not base64 !!");
    }

    private static String randomKey(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static TrackerProperties properties(String tokenKey) {
        return new TrackerProperties(
                "app", URI.create("https://jwks.test"), tokenKey, new TrackerProperties.Worker(null, null, null, null));
    }
}
