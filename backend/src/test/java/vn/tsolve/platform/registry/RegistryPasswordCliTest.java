package vn.tsolve.platform.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

class RegistryPasswordCliTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000251");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000252");

    private static String randomKeyBase64() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return "fake-mật-khẩu-'x';" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    @DisplayName("AC-001.24 prints only a v1: value that decrypts for this Workspace id only")
    void printsOnlyV1ValueDecryptableForWorkspaceIdOnly_AC_001_24() {
        String key = randomKeyBase64();
        String password = randomPassword();
        Map<String, String> env = Map.of(
                "CATALOG_SECRET_KEY", key,
                "TSOLVE_DEMO_WORKSPACE_ID", WORKSPACE_ID.toString(),
                "DB_PASSWORD", password);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exit = RegistryPasswordCli.run(
                env,
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        String out = outBytes.toString(StandardCharsets.UTF_8);
        String err = errBytes.toString(StandardCharsets.UTF_8);

        assertThat(exit).isZero();
        assertThat(err).isEmpty();
        assertThat(out.lines().toList()).hasSize(1);
        String line = out.strip();
        assertThat(line).startsWith("v1:").doesNotContain(password, key);

        PasswordCipher cipher =
                new PasswordCipher(new MockEnvironment().withProperty("tsolve.registry.secret-key", key));
        assertThat(cipher.decrypt(WORKSPACE_ID, line)).isEqualTo(password);
        assertThatThrownBy(() -> cipher.decrypt(OTHER_WORKSPACE_ID, line)).isInstanceOf(IllegalStateException.class);

        ByteArrayOutputStream secondOutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream secondErrBytes = new ByteArrayOutputStream();
        int secondExit = RegistryPasswordCli.run(
                env,
                new PrintStream(secondOutBytes, true, StandardCharsets.UTF_8),
                new PrintStream(secondErrBytes, true, StandardCharsets.UTF_8));
        assertThat(secondExit).isZero();
        assertThat(secondOutBytes.toString(StandardCharsets.UTF_8).strip()).isNotEqualTo(line);
    }

    @ParameterizedTest(name = "edge 14 {0}")
    @MethodSource("invalidEnvs")
    @DisplayName("edge 14 a missing or invalid variable is named and nothing secret is printed")
    void missingOrInvalidVariableIsNamedWithoutPrintingSecrets_edge_14(
            String expectedVar, Map<String, String> env, String secretKey, String secretPassword) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exit = RegistryPasswordCli.run(
                env,
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        String out = outBytes.toString(StandardCharsets.UTF_8);
        String err = errBytes.toString(StandardCharsets.UTF_8);

        assertThat(exit).isNotZero();
        assertThat(out).isEmpty();
        assertThat(err).contains(expectedVar);
        if (secretPassword != null && !secretPassword.isBlank()) {
            assertThat(out).doesNotContain(secretPassword);
            assertThat(err).doesNotContain(secretPassword);
        }
        if (secretKey != null && !secretKey.isBlank()) {
            assertThat(out).doesNotContain(secretKey);
            assertThat(err).doesNotContain(secretKey);
        }
    }

    static Stream<Arguments> invalidEnvs() {
        String validKey = randomKeyBase64();
        String validPassword = randomPassword();
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        return Stream.of(
                envCase("CATALOG_SECRET_KEY", null, WORKSPACE_ID.toString(), validPassword, null, validPassword),
                envCase("CATALOG_SECRET_KEY", "   ", WORKSPACE_ID.toString(), validPassword, null, validPassword),
                envCase(
                        "CATALOG_SECRET_KEY",
                        shortKey,
                        WORKSPACE_ID.toString(),
                        validPassword,
                        shortKey,
                        validPassword),
                envCase("TSOLVE_DEMO_WORKSPACE_ID", validKey, null, validPassword, validKey, validPassword),
                envCase("TSOLVE_DEMO_WORKSPACE_ID", validKey, "", validPassword, validKey, validPassword),
                envCase("TSOLVE_DEMO_WORKSPACE_ID", validKey, "not-a-uuid", validPassword, validKey, validPassword),
                envCase("DB_PASSWORD", validKey, WORKSPACE_ID.toString(), null, validKey, null),
                envCase("DB_PASSWORD", validKey, WORKSPACE_ID.toString(), "  ", validKey, null));
    }

    private static Arguments envCase(
            String expectedVar,
            String key,
            String workspaceId,
            String password,
            String secretKey,
            String secretPassword) {
        Map<String, String> env = new HashMap<>();
        if (key != null) {
            env.put("CATALOG_SECRET_KEY", key);
        }
        if (workspaceId != null) {
            env.put("TSOLVE_DEMO_WORKSPACE_ID", workspaceId);
        }
        if (password != null) {
            env.put("DB_PASSWORD", password);
        }
        return Arguments.of(expectedVar, Map.copyOf(env), secretKey, secretPassword);
    }
}
