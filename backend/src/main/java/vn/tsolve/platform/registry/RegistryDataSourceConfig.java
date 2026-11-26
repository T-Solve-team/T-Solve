package vn.tsolve.platform.registry;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.platform.service.impl.WorkspaceCatalogImpl;

/**
 * The Registry database: its own pool, transaction manager and changelog, loaded only when REGISTRY_DB_URL is set.
 *
 * <p>The three infrastructure beans are registered after the auto-configuration conditions ran and are not autowire
 * candidates: a regular {@code DataSource}, {@code SpringLiquibase} or transaction manager bean would make Boot back
 * off from the business DataSource, the tenant Liquibase and the JPA transaction manager (same trap as
 * EmailOutboxConfiguration).
 */
// ponytail: optional while REGISTRY_DB_* are not in every .env and test setup; drop the condition when routing needs
// the Registry (AC-008.5).
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${tsolve.registry.url:}'.isBlank()")
public class RegistryDataSourceConfig {

    private static final String CATALOG_CHANGELOG = "classpath:db/changelog/catalog/catalog-master.xml";

    @Bean
    static BeanFactoryPostProcessor registryInfrastructure() {
        return factory -> {
            var registry = (BeanDefinitionRegistry) factory;

            var dataSource = new RootBeanDefinition(HikariDataSource.class);
            dataSource.setAutowireCandidate(false);
            dataSource.setDestroyMethodName("close");
            dataSource.setInstanceSupplier(() -> {
                Environment environment = factory.getBean(Environment.class);
                var pool = new HikariDataSource();
                pool.setPoolName("registry");
                pool.setJdbcUrl(environment.getProperty("tsolve.registry.url"));
                pool.setUsername(environment.getProperty("tsolve.registry.username"));
                pool.setPassword(environment.getProperty("tsolve.registry.password"));
                pool.setMaximumPoolSize(5);
                return pool;
            });
            registry.registerBeanDefinition("registryDataSource", dataSource);

            var transactions = new RootBeanDefinition(DataSourceTransactionManager.class);
            transactions.setAutowireCandidate(false);
            transactions.setInstanceSupplier(() -> new DataSourceTransactionManager(
                    factory.getBean("registryDataSource", javax.sql.DataSource.class)));
            registry.registerBeanDefinition("registryTransactionManager", transactions);

            var jdbc = new RootBeanDefinition(JdbcClient.class);
            jdbc.setAutowireCandidate(false);
            jdbc.setInstanceSupplier(
                    () -> JdbcClient.create(factory.getBean("registryDataSource", javax.sql.DataSource.class)));
            registry.registerBeanDefinition("registryJdbcClient", jdbc);

            var liquibase = new RootBeanDefinition(SpringLiquibase.class);
            liquibase.setAutowireCandidate(false);
            liquibase.setInstanceSupplier(() -> {
                var migration = new SpringLiquibase();
                migration.setDataSource(factory.getBean("registryDataSource", javax.sql.DataSource.class));
                migration.setChangeLog(CATALOG_CHANGELOG);
                // The worker process never migrates the Registry (AC-008.4); it only reads it.
                Environment environment = factory.getBean(Environment.class);
                migration.setShouldRun(!environment.acceptsProfiles(Profiles.of("worker"))
                        && environment.getProperty("tsolve.registry.liquibase.enabled", Boolean.class, true));
                return migration;
            });
            registry.registerBeanDefinition("registryLiquibase", liquibase);
        };
    }

    @Bean
    PasswordCipher passwordCipher(Environment environment) {
        return new PasswordCipher(environment);
    }

    @Bean
    RegistryRepository registryRepository(BeanFactory beanFactory) {
        return new RegistryRepository(beanFactory.getBean("registryJdbcClient", JdbcClient.class));
    }

    @Bean
    WorkspaceCatalog workspaceCatalog(RegistryRepository registryRepository, TenantContext tenantContext) {
        return new WorkspaceCatalogImpl(registryRepository, tenantContext);
    }
}
