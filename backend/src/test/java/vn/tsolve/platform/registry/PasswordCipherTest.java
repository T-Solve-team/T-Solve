package vn.tsolve.platform.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

class PasswordCipherTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(filled(32, (byte) 1));
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000251");
    private static final UUID OTHER_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000252");
    private static final String PASSWORD = "fake-db-password-p@ss;'w0rd";
    private static final String UNICODE_PASSWORD = "fake-mật-khẩu-秘密-'x';";

    private final PasswordCipher cipher = cipher(KEY);

    @Test
    @DisplayName("AC-001.24 round trip; stored value is v1: + nonce + GCM ciphertext")
    void roundTripsAndStoresNoPlaintext_AC_001_24() {
        for (String password : new String[] {PASSWORD, UNICODE_PASSWORD}) {
            String stored = cipher.encrypt(WORKSPACE, password);

            assertThat(stored).startsWith("v1:").doesNotContain(password);
            byte[] packed = Base64.getDecoder().decode(stored.substring(3));
            assertThat(packed).hasSize(12 + password.getBytes(StandardCharsets.UTF_8).length + 16);
            assertThat(cipher.decrypt(WORKSPACE, stored)).isEqualTo(password);
            assertThat(cipher.encrypt(WORKSPACE, password)).isNotEqualTo(stored);
        }
    }

    @Test
    @DisplayName("AC-001.24 ciphertext copied to another row does not decrypt")
    void copiedCiphertextDoesNotDecryptOnAnotherWorkspace_AC_001_24() {
        String stored = cipher.encrypt(WORKSPACE, PASSWORD);

        assertThatThrownBy(() -> cipher.decrypt(OTHER_WORKSPACE, stored))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(PASSWORD, stored))
                .hasNoCause();
    }

    @ParameterizedTest(name = "AC-001.25 edge 13 edge 14 case {index}")
    @MethodSource("badStoredValues")
    @DisplayName("AC-001.25 edge 13 edge 14 wrong key, tampered or malformed value refuses; message holds no secret")
    void wrongKeyOrTamperedValueRefusesWithoutSecret_AC_001_25_edge_13_edge_14(String label, String stored) {
        assertThatThrownBy(() -> cipher.decrypt(WORKSPACE, stored))
                .as(label)
                .isInstanceOf(IllegalStateException.class)
                .satisfies(failure -> {
                    assertThat(failure.getMessage()).isNotBlank().doesNotContain(PASSWORD, KEY, OTHER_KEY);
                    if (stored != null && !stored.isBlank()) {
                        assertThat(failure.getMessage()).doesNotContain(stored);
                    }
                })
                .hasNoCause();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> badStoredValues() {
        PasswordCipher keyA = cipher(KEY);
        String valid = keyA.encrypt(WORKSPACE, PASSWORD);
        byte[] flipped = Base64.getDecoder().decode(valid.substring(3));
        flipped[flipped.length - 1] ^= 1;
        return Stream.of(
                args("encrypted under another key", cipher(OTHER_KEY).encrypt(WORKSPACE, PASSWORD)),
                args("one flipped byte", "v1:" + Base64.getEncoder().encodeToString(flipped)),
                args("v2 prefix", "v2:" + valid.substring(3)),
                args("no prefix", valid.substring(3)),
                args("shorter than nonce plus tag", "v1:" + Base64.getEncoder().encodeToString(new byte[27])),
                args("not base64", "v1:%%not-base64%%"),
                args("null", null));
    }

    private static org.junit.jupiter.params.provider.Arguments args(String label, String stored) {
        return org.junit.jupiter.params.provider.Arguments.of(label, stored);
    }

    @ParameterizedTest(name = "AC-001.25 edge 13 key case {index}")
    @MethodSource("invalidKeys")
    @DisplayName("AC-001.25 edge 13 missing or invalid CATALOG_SECRET_KEY refuses and names the variable")
    void missingOrInvalidKeyNamesTheVariable_AC_001_25_edge_13(String key) {
        MockEnvironment environment = new MockEnvironment();
        if (key != null) {
            environment.setProperty("tsolve.registry.secret-key", key);
        }

        assertThatThrownBy(() -> new PasswordCipher(environment))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(failure -> {
                    assertThat(failure.getMessage()).contains("CATALOG_SECRET_KEY");
                    if (key != null && !key.isBlank()) {
                        assertThat(failure.getMessage()).doesNotContain(key);
                    }
                });
    }

    static Stream<String> invalidKeys() {
        return Stream.of(
                null,
                "",
                " ",
                "obviously-fake-invalid-key!",
                Base64.getEncoder().encodeToString(new byte[16]),
                Base64.getEncoder().encodeToString(new byte[33]));
    }

    private static PasswordCipher cipher(String key) {
        return new PasswordCipher(new MockEnvironment().withProperty("tsolve.registry.secret-key", key));
    }

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
