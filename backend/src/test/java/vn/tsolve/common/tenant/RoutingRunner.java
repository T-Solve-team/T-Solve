package vn.tsolve.common.tenant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import tsolvetest.tenant.IsolationProbe;
import vn.tsolve.platform.registry.RegistryDataSourceConfig;
import vn.tsolve.platform.registry.TenantDataSourceConfig;

/**
 * The routing context of the Task 6-2 tests: the main {@code application.yml}, no machine environment, exactly the
 * given variables, the Registry and tenant routing configuration and Boot's JDBC and JPA auto-configurations.
 */
public final class RoutingRunner {

    private RoutingRunner() {}

    /** The Registry, the routing configuration and the holder, without any auto-configuration. */
    public static ApplicationContextRunner configurationOnly(Map<String, String> variables) {
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
                    sources.addFirst(new MapPropertySource("routingVariables", new LinkedHashMap<>(variables)));
                })
                .withPropertyValues("spring.liquibase.enabled=false", "tsolve.registry.liquibase.enabled=false")
                .withUserConfiguration(
                        RegistryDataSourceConfig.class, TenantDataSourceConfig.class, AcceptingKnownWorkspaces.class);
    }

    /** {@link #configurationOnly} plus the JDBC and JPA auto-configurations and the {@link IsolationProbe} entity. */
    public static ApplicationContextRunner routing(Map<String, String> variables) {
        return configurationOnly(variables)
                .withUserConfiguration(ProbeEntities.class)
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class,
                        TransactionAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = IsolationProbe.class)
    static class ProbeEntities {}
}
