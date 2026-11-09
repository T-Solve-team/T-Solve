package vn.tsolve.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import vn.tsolve.identity.signin.port.NoopWorkspaceSignInProvisioner;
import vn.tsolve.identity.signin.port.WorkspaceSignInProvisioner;

/**
 * Which bean of the port a profile gets (AC-001.60, AC-001.61, edge 109). The context holds only what is scanned from
 * the provisioning packages ({@code identity.keycloak} and {@code identity.signin.port}, without the sign-in lookups)
 * plus the real application.yml; the developer's own environment is left out. The only outside bean is a
 * {@link DataSource} for the lock. No Docker.
 */
class ProvisioningProfileTest {

    private static final String SECRET_ENV = "KEYCLOAK_ADMIN_CLIENT_SECRET";
    private static final String GOOGLE_ID_ENV = "GOOGLE_CLIENT_ID";
    private static final String GOOGLE_SECRET_ENV = "GOOGLE_CLIENT_SECRET";
    private static final String LOGIN_ORIGIN_ENV = "TSOLVE_LOGIN_ORIGIN";
    private static final String SITE_TEMPLATE_ENV = "TSOLVE_SITE_ORIGIN_TEMPLATE";
    private static final String SECRET = "profile-test-admin-secret";
    private static final String GOOGLE_ID = "profile-test-google-id";
    private static final String GOOGLE_SECRET = "profile-test-google-secret";

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = {"vn.tsolve.identity.keycloak", "vn.tsolve.identity.signin.port"},
            excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern =
                                ".*\\.(NoWorkspaceLookup|LocalTenantScope|InMemoryWorkspaceLookup|FakeWorkspaceSignInProvisioner|KeycloakProvisioningFixture.*|.*Test|.*IT)"),
            })
    @ConfigurationPropertiesScan("vn.tsolve.identity.keycloak")
    static class ProvisioningPackages {}

    private static Map<String, String> allThree() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put(SECRET_ENV, SECRET);
        env.put(GOOGLE_ID_ENV, GOOGLE_ID);
        env.put(GOOGLE_SECRET_ENV, GOOGLE_SECRET);
        env.put(LOGIN_ORIGIN_ENV, "https://login.example.test");
        env.put(SITE_TEMPLATE_ENV, "https://{slug}.example.test");
        return env;
    }

    private static String messagesOf(Throwable failure) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            chain.append(t.getMessage()).append('\n');
        }
        return chain.toString();
    }

    private record Misconfiguration(String variable, String value, String leaked) {}

    @Test
    @DisplayName("AC-001.61 toString of the provisioning properties holds neither the admin secret nor the Google one")
    void toStringHoldsNoSecret_AC_001_61() {
        KeycloakProvisioningProperties properties = new KeycloakProvisioningProperties(
                "https://login.example.test",
                "tsolve",
                SECRET,
                GOOGLE_ID,
                GOOGLE_SECRET,
                "https://{slug}.example.test");

        assertThat(properties.toString()).doesNotContain(SECRET).doesNotContain(GOOGLE_SECRET);
    }

    @Test
    @DisplayName(
            "AC-001.61 S5 in ops with the three secrets set, an empty or unusable TSOLVE_LOGIN_ORIGIN or TSOLVE_SITE_ORIGIN_TEMPLATE fails the start-up and names the variable, not its value")
    void unusableOriginsFailStartUp_AC_001_61_S5() {
        List<Misconfiguration> cases = List.of(
                new Misconfiguration(LOGIN_ORIGIN_ENV, null, null),
                new Misconfiguration(LOGIN_ORIGIN_ENV, "", null),
                new Misconfiguration(LOGIN_ORIGIN_ENV, "http://keycloak.example.test", "keycloak.example.test"),
                new Misconfiguration(SITE_TEMPLATE_ENV, null, null),
                new Misconfiguration(SITE_TEMPLATE_ENV, "", null),
                new Misconfiguration(SITE_TEMPLATE_ENV, "https://sites.example.test", "sites.example.test"));
        for (Misconfiguration bad : cases) {
            Map<String, String> env = allThree();
            if (bad.value() == null) {
                env.remove(bad.variable());
            } else {
                env.put(bad.variable(), bad.value());
            }
            runner(env, "ops").run(context -> {
                assertThat(context.getStartupFailure())
                        .as("start-up with %s = %s", bad.variable(), bad.value())
                        .isNotNull();
                String messages = messagesOf(context.getStartupFailure());
                assertThat(messages)
                        .contains(bad.variable())
                        .doesNotContain(SECRET)
                        .doesNotContain(GOOGLE_SECRET);
                if (bad.leaked() != null) {
                    assertThat(messages).as("the message holds no value").doesNotContain(bad.leaked());
                }
            });
        }
    }

    @Test
    @DisplayName("AC-001.61 S5 N8 http is accepted for localhost and 127.0.0.1 only; https is accepted everywhere")
    void plainHttpOnlyForLoopback_AC_001_61_N8() {
        List<String> acceptedOrigins =
                List.of("http://localhost:8080", "http://127.0.0.1:8080", "https://login.example.test");
        for (String accepted : acceptedOrigins) {
            Map<String, String> env = allThree();
            env.put(LOGIN_ORIGIN_ENV, accepted);
            runner(env, "ops").run(context -> {
                assertThat(context.getStartupFailure())
                        .as("start-up with %s", accepted)
                        .isNull();
                assertThat(context.getBeansOfType(WorkspaceSignInProvisioner.class))
                        .hasSize(1)
                        .allSatisfy((name, bean) ->
                                assertThat(bean).isInstanceOf(KeycloakWorkspaceSignInProvisioner.class));
            });
        }
        for (String refused : List.of("http://keycloak.example.test", "http://localhost.example.test")) {
            Map<String, String> env = allThree();
            env.put(LOGIN_ORIGIN_ENV, refused);
            runner(env, "ops").run(context -> {
                assertThat(context.getStartupFailure())
                        .as("start-up with %s", refused)
                        .isNotNull();
                assertThat(messagesOf(context.getStartupFailure()))
                        .contains(LOGIN_ORIGIN_ENV)
                        .doesNotContain(SECRET)
                        .doesNotContain(refused);
            });
        }
    }

    @Test
    @DisplayName("AC-001.60 S5 with a secret missing the no-op bean is wired even when the origins are empty")
    void missingSecretStillWiresNoopWithoutOrigins_AC_001_60_S5() {
        Map<String, String> env = allThree();
        env.remove(GOOGLE_ID_ENV);
        env.remove(LOGIN_ORIGIN_ENV);
        env.remove(SITE_TEMPLATE_ENV);
        runner(env, "ops").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBeansOfType(WorkspaceSignInProvisioner.class))
                    .hasSize(1)
                    .allSatisfy((name, bean) -> assertThat(bean).isInstanceOf(NoopWorkspaceSignInProvisioner.class));
        });
    }

    private static ApplicationContextRunner runner(Map<String, String> env, String... profiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    onlyApplicationYml(context);
                    context.getEnvironment().setActiveProfiles(profiles);
                })
                .withPropertyValues(env.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withUserConfiguration(ProvisioningPackages.class);
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

    private static void assertOnlyTheNoop(org.springframework.context.ApplicationContext context) {
        assertThat(context.getBeansOfType(WorkspaceSignInProvisioner.class))
                .hasSize(1)
                .allSatisfy((name, bean) -> assertThat(bean).isInstanceOf(NoopWorkspaceSignInProvisioner.class));
        assertThat(context.getBeansOfType(KeycloakProvisioningProperties.class)).isEmpty();
        assertThat(context.getBeansOfType(KeycloakAdminClient.class)).isEmpty();
        assertThat(context.getBeansOfType(ProvisioningLock.class)).isEmpty();
    }

    @Test
    @DisplayName(
            "AC-001.61 edge 109 a Workspace web profile starts without any Keycloak Admin or Google value and loads none of them")
    void webProfileLoadsNoProvisioningValues_AC_001_61_edge109() {
        // no value at all
        runner(Map.of()).run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertOnlyTheNoop(context);
        });
        // the three values are in the environment, but the process is not `ops`: they are never read
        runner(allThree()).run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertOnlyTheNoop(context);
        });
        runner(allThree(), "dev").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertOnlyTheNoop(context);
        });
    }

    @Test
    @DisplayName("AC-001.61 the ops profile loads the provisioner and Google values and wires the Keycloak adapter")
    void opsProfileWiresKeycloakAdapter_AC_001_61() {
        runner(allThree(), "ops").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBeansOfType(WorkspaceSignInProvisioner.class))
                    .hasSize(1)
                    .allSatisfy(
                            (name, bean) -> assertThat(bean).isInstanceOf(KeycloakWorkspaceSignInProvisioner.class));
            KeycloakProvisioningProperties properties = context.getBean(KeycloakProvisioningProperties.class);
            assertThat(properties.adminClientSecret()).isEqualTo(SECRET);
            assertThat(properties.googleClientId()).isEqualTo(GOOGLE_ID);
            assertThat(properties.googleClientSecret()).isEqualTo(GOOGLE_SECRET);
        });

        // Q2: any value missing -> the no-op bean and one WARN naming the missing variable
        for (String missing : List.of(SECRET_ENV, GOOGLE_ID_ENV, GOOGLE_SECRET_ENV)) {
            Map<String, String> env = allThree();
            env.remove(missing);
            try (KeycloakProvisioningFixture.CapturedLogs logs = new KeycloakProvisioningFixture.CapturedLogs()) {
                runner(env, "ops").run(context -> {
                    assertThat(context.getStartupFailure())
                            .as("start-up without " + missing)
                            .isNull();
                    assertThat(context.getBeansOfType(WorkspaceSignInProvisioner.class))
                            .hasSize(1)
                            .allSatisfy((name, bean) ->
                                    assertThat(bean).isInstanceOf(NoopWorkspaceSignInProvisioner.class));
                });
                assertThat(logs.lines(Level.WARN))
                        .as("one WARN naming " + missing)
                        .hasSize(1)
                        .allSatisfy(line -> assertThat(line)
                                .contains(missing)
                                .doesNotContain(SECRET)
                                .doesNotContain(GOOGLE_SECRET));
            }
        }
    }
}
