package vn.tsolve.identity.signin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Binds {@link SignInProperties} through the real application.yml. The developer's own environment, system
 * properties and .env file are left out, so a missing value cannot be masked by them.
 */
class SignInPropertiesTest {

    private static final String SIGNING_KEY = "TSOLVE_SIGNING_KEY";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SignInProperties.class)
    static class PropertiesConfig {}

    private static Map<String, String> validEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("TSOLVE_LOGIN_ORIGIN", SignInTestFixtures.LOGIN_ORIGIN);
        env.put("TSOLVE_ADMIN_ORIGIN", SignInTestFixtures.ADMIN_ORIGIN);
        env.put(SIGNING_KEY, SignInTestFixtures.KEY);
        env.put("GOOGLE_CLIENT_ID", "test-client-id");
        env.put("GOOGLE_CLIENT_SECRET", "test-secret");
        env.put("PLATFORM_OPERATOR_EMAILS", "ops@example.test");
        return env;
    }

    private static ApplicationContextRunner runner(Map<String, String> env) {
        return new ApplicationContextRunner()
                .withInitializer(SignInPropertiesTest::onlyApplicationYml)
                .withPropertyValues(env.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new))
                .withUserConfiguration(PropertiesConfig.class);
    }

    private static void onlyApplicationYml(ConfigurableApplicationContext context) {
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try {
            for (PropertySource<?> source :
                    new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))) {
                sources.addLast(source);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every message in the cause chain, so a test can look for the env name wherever Spring put it. */
    private static String failureText(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            text.append(cause).append('\n');
        }
        return text.toString();
    }

    private static void assertFailsNaming(Map<String, String> env, String envName) {
        runner(env).run(context -> {
            assertThat(context.getStartupFailure()).as("startup failure").isNotNull();
            assertThat(failureText(context.getStartupFailure())).contains(envName);
        });
    }

    private static String base64OfBytes(int count) {
        byte[] bytes = new byte[count];
        for (int i = 0; i < count; i++) {
            bytes[i] = (byte) ('a' + i % 26);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("edge 38 starts when every sign-in value is set")
    void startsWhenEverySignInValueIsSet_edge38() {
        runner(validEnvironment()).run(context -> {
            assertThat(context).hasNotFailed();
            SignInProperties properties = context.getBean(SignInProperties.class);
            assertThat(properties.loginOrigin()).isEqualTo(URI.create(SignInTestFixtures.LOGIN_ORIGIN));
            assertThat(properties.adminOrigin()).isEqualTo(URI.create(SignInTestFixtures.ADMIN_ORIGIN));
            assertThat(properties.signingKey()).isEqualTo(SignInTestFixtures.KEY);
            assertThat(properties.googleClientId()).isEqualTo("test-client-id");
            assertThat(properties.googleClientSecret()).isEqualTo("test-secret");
            assertThat(properties.operatorEmails()).isEqualTo("ops@example.test");
        });
    }

    static Stream<Arguments> missingValues() {
        return validEnvironment().keySet().stream()
                .flatMap(name -> Stream.of(Arguments.of(name, "absent"), Arguments.of(name, "blank")));
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("missingValues")
    @DisplayName("edge 38 fails at startup and names a missing or blank value")
    void failsAtStartupNamingMissingValue_edge38(String envName, String how) {
        Map<String, String> env = validEnvironment();
        if (how.equals("absent")) {
            env.remove(envName);
        } else {
            env.put(envName, "");
        }

        assertFailsNaming(env, envName);
    }

    @Test
    @DisplayName("edge 38 refuses a signing key shorter than 32 bytes")
    void refusesSigningKeyShorterThan32Bytes_edge38() {
        Map<String, String> shortKey = validEnvironment();
        shortKey.put(SIGNING_KEY, base64OfBytes(31));
        assertFailsNaming(shortKey, SIGNING_KEY);

        Map<String, String> exactKey = validEnvironment();
        exactKey.put(SIGNING_KEY, base64OfBytes(32));
        runner(exactKey).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("edge 38 refuses a signing key that is not base64")
    void refusesSigningKeyThatIsNotBase64_edge38() {
        Map<String, String> env = validEnvironment();
        env.put(SIGNING_KEY, "not*base64!");

        assertFailsNaming(env, SIGNING_KEY);
    }

    @ParameterizedTest(name = "[{index}] {0}={1}")
    @CsvSource({"TSOLVE_LOGIN_ORIGIN, login.localhost", "TSOLVE_ADMIN_ORIGIN, admin.localhost"})
    @DisplayName("edge 38 refuses an origin without a scheme")
    void refusesOriginWithoutScheme_edge38(String envName, String value) {
        Map<String, String> env = validEnvironment();
        env.put(envName, value);

        assertFailsNaming(env, envName);
    }
}
