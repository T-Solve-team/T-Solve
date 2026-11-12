package vn.tsolve.workspace.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import vn.tsolve.workspace.entity.WorkspaceInfo;

@ExtendWith(OutputCaptureExtension.class)
class TokenCipherTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String TOKEN = "obviously-fake-jira-token-秘密";
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000119");

    private final TokenCipher cipher = cipher(KEY);

    @Test
    @DisplayName("AC-007.5 encrypts with AES-256-GCM and a 96-bit nonce in the v1 envelope")
    void encryptsWithTheSpecifiedEnvelope() throws Exception {
        String encrypted = cipher.encrypt(WORKSPACE, TOKEN);

        assertThat(encrypted).startsWith("v1:").doesNotContain(TOKEN);
        byte[] packed = Base64.getDecoder().decode(encrypted.substring(3));
        assertThat(packed).hasSize(12 + TOKEN.getBytes(StandardCharsets.UTF_8).length + 16);
        Cipher independent = Cipher.getInstance("AES/GCM/NoPadding");
        independent.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(Base64.getDecoder().decode(KEY), "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(packed, 0, 12)));
        independent.updateAAD(ByteBuffer.allocate(16)
                .putLong(WORKSPACE.getMostSignificantBits())
                .putLong(WORKSPACE.getLeastSignificantBits())
                .array());

        assertThat(new String(independent.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8))
                .isEqualTo(TOKEN);
        assertThat(cipher.decrypt(WORKSPACE, encrypted)).isEqualTo(TOKEN);
    }

    @Test
    @DisplayName("AC-007.5 generates a fresh nonce even for the same Workspace and token")
    void generatesFreshNonces() {
        String first = cipher.encrypt(WORKSPACE, TOKEN);
        String second = cipher.encrypt(WORKSPACE, TOKEN);

        byte[] firstNonce = Arrays.copyOf(Base64.getDecoder().decode(first.substring(3)), 12);
        byte[] secondNonce = Arrays.copyOf(Base64.getDecoder().decode(second.substring(3)), 12);
        assertThat(firstNonce).isNotEqualTo(secondNonce);
        assertThat(cipher.decrypt(WORKSPACE, first)).isEqualTo(TOKEN);
        assertThat(cipher.decrypt(WORKSPACE, second)).isEqualTo(TOKEN);
    }

    @Test
    @DisplayName("AC-007.5 refuses ciphertext copied to another Workspace")
    void bindsCiphertextToTheWorkspace() {
        String encrypted = cipher.encrypt(WORKSPACE, TOKEN);

        assertThatThrownBy(() -> cipher.decrypt(new UUID(119, 2), encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace token decryption failed")
                .hasNoCause();
    }

    @ParameterizedTest(name = "AC-007.5 rejects tampering at byte {0}")
    @ValueSource(ints = {0, 12, -1})
    @DisplayName("AC-007.5 authenticates the nonce ciphertext and tag")
    void rejectsTampering(int position) {
        byte[] packed =
                Base64.getDecoder().decode(cipher.encrypt(WORKSPACE, TOKEN).substring(3));
        packed[position < 0 ? packed.length - 1 : position] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(packed);

        assertThatThrownBy(() -> cipher.decrypt(WORKSPACE, tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace token decryption failed")
                .hasNoCause();
    }

    @Test
    @DisplayName("AC-007.5 refuses a value encrypted under a different key")
    void rejectsTheWrongKey() {
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 1);
        TokenCipher other = cipher(Base64.getEncoder().encodeToString(otherKey));
        String encrypted = cipher.encrypt(WORKSPACE, TOKEN);

        assertThatThrownBy(() -> other.decrypt(WORKSPACE, encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace token decryption failed")
                .hasNoCause();
    }

    @Test
    @DisplayName("AC-007.6 starts with a Base64 encoded 32-byte WORKSPACE_SECRET_KEY")
    void startsWithAValidKey() {
        runner(KEY).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TokenCipher.class);
            TokenCipher configured = context.getBean(TokenCipher.class);
            assertThat(configured.decrypt(WORKSPACE, configured.encrypt(WORKSPACE, TOKEN)))
                    .isEqualTo(TOKEN);
        });
    }

    @Test
    @DisplayName("AC-007.6 shared test configuration supplies the fake key for existing application contexts")
    void startsWithTheSharedTestConfiguration() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(TokenCipher.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(TokenCipher.class);
                    assertThat(context.getBean(TokenCipher.class).decrypt(WORKSPACE, cipher.encrypt(WORKSPACE, TOKEN)))
                            .isEqualTo(TOKEN);
                });
    }

    @Test
    @DisplayName("AC-007.6 refuses startup with a missing WORKSPACE_SECRET_KEY")
    void refusesAMissingKey() {
        runner(null).run(context -> {
            assertThat(context).hasFailed();
            assertThat(stackTrace(context.getStartupFailure())).contains("WORKSPACE_SECRET_KEY");
        });
    }

    @ParameterizedTest(name = "AC-007.6 rejects invalid key case {index}")
    @MethodSource("invalidKeys")
    @DisplayName("AC-007.6 refuses blank malformed or wrong-length keys without exposing the value")
    void refusesInvalidKeys(String invalid, CapturedOutput output) {
        runner(invalid).run(context -> {
            assertThat(context).hasFailed();
            String failure = stackTrace(context.getStartupFailure());
            assertThat(failure).contains("WORKSPACE_SECRET_KEY");
            if (!invalid.isBlank()) {
                assertThat(failure).doesNotContain(invalid);
                assertThat(output.getAll()).doesNotContain(invalid);
            }
        });
    }

    static Stream<String> invalidKeys() {
        return Stream.concat(
                Stream.of("", " ", "obviously-fake-invalid-key!", "${UNSET_FAKE_KEY}"),
                Stream.of(16, 24, 31, 33).map(length -> Base64.getEncoder().encodeToString(new byte[length])));
    }

    @ParameterizedTest(name = "AC-007.7 sanitizes malformed ciphertext case {index}")
    @MethodSource("invalidCiphertexts")
    @DisplayName("AC-007.7 decryption diagnostics never expose ciphertext or decoder causes")
    void sanitizesDecryptionFailures(String invalid, CapturedOutput output) {
        assertThatThrownBy(() -> cipher.decrypt(WORKSPACE, invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace token decryption failed")
                .hasNoCause();
        assertThat(output.getAll()).doesNotContain(TOKEN);
        if (invalid != null && !invalid.isBlank()) {
            assertThat(output.getAll()).doesNotContain(invalid);
        }
    }

    static Stream<String> invalidCiphertexts() {
        return Stream.of(
                null,
                "",
                TOKEN,
                "v2:" + TOKEN,
                "v1:" + TOKEN,
                "v1:AA==",
                "v1:" + Base64.getEncoder().encodeToString(new byte[28]));
    }

    @Test
    @DisplayName("AC-007.7 encryption failures never expose the input token")
    void sanitizesEncryptionFailures(CapturedOutput output) {
        assertThatThrownBy(() -> cipher.encrypt(null, TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace token encryption failed")
                .hasNoCause();
        assertThatThrownBy(() -> cipher.encrypt(WORKSPACE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace token encryption failed")
                .hasNoCause();
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("AC-007.7 entity and cipher diagnostics and entity JSON hide secret values")
    void hidesSecretsInDiagnosticsAndJson() throws Exception {
        String encrypted = cipher.encrypt(WORKSPACE, TOKEN);
        WorkspaceInfo workspace = new WorkspaceInfo(
                WORKSPACE, "Fake company", "https://fake.atlassian.net", "fake@example.test", encrypted);

        assertThat(workspace.getServiceAccountApiToken()).isEqualTo(encrypted);
        assertThat(workspace.toString()).doesNotContain(TOKEN, encrypted, KEY);
        assertThat(cipher.toString()).doesNotContain(TOKEN, encrypted, KEY);
        assertThat(new ObjectMapper().writeValueAsString(workspace))
                .doesNotContain(TOKEN, encrypted, "serviceAccountApiToken");
    }

    private static TokenCipher cipher(String key) {
        return new TokenCipher(new MockEnvironment().withProperty("tsolve.workspace.secret-key", key));
    }

    private static ApplicationContextRunner runner(String key) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    try {
                        for (var source : new YamlPropertySourceLoader()
                                .load("application.yml", new ClassPathResource("application.yml"))) {
                            sources.addLast(source);
                        }
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                    if (key != null) {
                        context.getEnvironment()
                                .getPropertySources()
                                .addFirst(new org.springframework.core.env.MapPropertySource(
                                        "fakeWorkspaceKey", java.util.Map.of("WORKSPACE_SECRET_KEY", key)));
                    }
                })
                .withUserConfiguration(TokenCipher.class);
    }

    private static String stackTrace(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
