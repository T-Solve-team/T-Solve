package vn.tsolve.platform.tenantdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.tenant.AcceptingKnownWorkspaces;
import vn.tsolve.platform.registry.RegistryDataSourceConfig;
import vn.tsolve.platform.registry.TenantDataSourceConfig;

/**
 * Where the startup migrator runs and what happens to Boot's tenant Liquibase there. No database: every
 * {@link SpringLiquibase} is a spy that does not run and {@link TenantMigrator} is a mock.
 */
class TenantStartupMigratorTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final TenantMigrator migrator = mock(TenantMigrator.class);
    private final Map<String, SpringLiquibase> liquibaseSpies = new LinkedHashMap<>();

    @Test
    @DisplayName("AC-008.4 the web process migrates all databases once, after the Registry changelog, and Boot's "
            + "tenant Liquibase is off")
    void webRunsAllMigrationsAfterTheRegistryChangelog_AC_008_4() {
        routing().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TenantStartupMigrator.class);
            var order = inOrder(liquibaseSpies.get("registryLiquibase"), migrator);
            order.verify(liquibaseSpies.get("registryLiquibase")).afterPropertiesSet();
            order.verify(migrator).applyAll();
            verify(migrator).applyAll();
            assertThat(context).doesNotHaveBean("liquibase");
        });
    }

    @Test
    @DisplayName("AC-008.9 a failed migration stops the context start with the site name")
    void failedMigrationStopsStartup_AC_008_9() {
        doThrow(new IllegalStateException("Migration of Workspace site-bravo failed"))
                .when(migrator)
                .applyAll();

        routing().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("site-bravo");
        });
    }

    @Test
    @DisplayName("AC-008.4 the worker process never runs Liquibase on the tenant databases")
    void workerNeverRunsLiquibase_AC_008_4() {
        runner(registryVariables(), "application-worker.yml", "application.yml")
                .withPropertyValues("spring.profiles.active=worker")
                .withUserConfiguration(NoEntities.class)
                .withConfiguration(routingAutoConfigurations())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (context.containsBean("liquibase")) {
                        assertThat(ReflectionTestUtils.getField(liquibaseSpies.get("liquibase"), "shouldRun"))
                                .as("Boot's tenant Liquibase would run in the worker")
                                .isEqualTo(false);
                    }
                    assertThat(context).doesNotHaveBean(TenantStartupMigrator.class);
                    verify(migrator, never()).applyAll();
                });
    }

    @ParameterizedTest(name = "AC-008.4 routing off ({0}): Boot's tenant Liquibase stays, no startup migrator")
    @MethodSource("routingOff")
    @DisplayName("AC-008.4 where routing is off Boot's tenant Liquibase stays on the primary DataSource")
    void keepsBootLiquibaseWhereRoutingIsOff_AC_008_4(
            String label, Map<String, Object> variables, String[] properties) {
        runner(variables, "application.yml")
                .withPropertyValues(properties)
                .withConfiguration(
                        AutoConfigurations.of(DataSourceAutoConfiguration.class, LiquibaseAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("liquibase");
                    SpringLiquibase liquibase = context.getBean("liquibase", SpringLiquibase.class);
                    assertThat(liquibase.getChangeLog()).endsWith("db/changelog/db.changelog-master.xml");
                    assertThat(liquibase.getDataSource()).isSameAs(context.getBean(DataSource.class));
                    assertThat(ReflectionTestUtils.getField(liquibase, "shouldRun"))
                            .isEqualTo(true);
                    assertThat(context).doesNotHaveBean(TenantStartupMigrator.class);
                    verify(migrator, never()).applyAll();
                });
    }

    static Stream<Arguments> routingOff() {
        var noRegistry = new LinkedHashMap<>(registryVariables());
        noRegistry.put("REGISTRY_DB_URL", "");
        return Stream.of(
                Arguments.of("no Registry URL", noRegistry, new String[0]),
                Arguments.of("dev profile", registryVariables(), new String[] {"spring.profiles.active=dev"}));
    }

    // ---- helpers ----

    private ApplicationContextRunner routing() {
        return runner(registryVariables(), "application.yml")
                .withUserConfiguration(NoEntities.class)
                .withConfiguration(routingAutoConfigurations());
    }

    private static AutoConfigurations routingAutoConfigurations() {
        return AutoConfigurations.of(
                DataSourceAutoConfiguration.class,
                LiquibaseAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                TransactionAutoConfiguration.class);
    }

    private static Map<String, Object> registryVariables() {
        return Map.of(
                "REGISTRY_DB_URL", "jdbc:postgresql://registry.invalid:5432/registry",
                "REGISTRY_DB_USER", "registry_user",
                "REGISTRY_DB_PASSWORD", "fake-registry-password",
                "CATALOG_SECRET_KEY", KEY,
                "TENANT_DB_HOST", "tenant.invalid",
                "DB_HOST", "app.invalid",
                "DB_PORT", "5432",
                "DB_NAME", "app",
                "DB_USER", "app_user",
                "DB_PASSWORD", "fake-app-password");
    }

    /** The given yml files (first wins), no machine environment, exactly the given variables. */
    private ApplicationContextRunner runner(Map<String, Object> variables, String... ymlFiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    try {
                        for (String file : ymlFiles) {
                            for (var source : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                                sources.addLast(source);
                            }
                        }
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                    sources.addFirst(new MapPropertySource("variables", new LinkedHashMap<>(variables)));
                })
                .withBean("skipMigrations", BeanPostProcessor.class, this::skipMigrations)
                .withBean(TenantMigrator.class, () -> migrator)
                .withUserConfiguration(
                        RegistryDataSourceConfig.class,
                        TenantDataSourceConfig.class,
                        AcceptingKnownWorkspaces.class,
                        TenantStartupMigrator.class);
    }

    private BeanPostProcessor skipMigrations() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof SpringLiquibase liquibase) {
                    var migration = spy(liquibase);
                    try {
                        doNothing().when(migration).afterPropertiesSet();
                    } catch (liquibase.exception.LiquibaseException failure) {
                        throw new IllegalStateException(failure);
                    }
                    liquibaseSpies.put(name, migration);
                    return migration;
                }
                return bean;
            }
        };
    }

    /** JPA starts as in the application, with no entity. */
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackages = "tsolvetest.none")
    static class NoEntities {}
}
