package vn.tsolve.platform.registry;

import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseConnectionDetails;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tsolve.common.tenant.TenantAwareJpaTransactionManager;
import vn.tsolve.common.tenant.TenantDataSources;
import vn.tsolve.common.tenant.TenantRoutingDataSource;

/**
 * Tenant routing, on exactly when the Registry is configured and the profile is not {@code dev}: the primary
 * {@code DataSource} then routes by the bound Workspace. Otherwise Boot's single {@code DataSource} stays.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${tsolve.registry.url:}'.isBlank()")
@Profile("!dev")
public class TenantDataSourceConfig {

    @Bean(destroyMethod = "close")
    TenantDataSources tenantDataSources(
            RegistryRepository registry,
            PasswordCipher cipher,
            @Value("${tsolve.tenant-db.host}") String host,
            @Value("${tsolve.tenant-db.port}") int port,
            @Value("${tsolve.tenant-db.pool-max}") int poolMax) {
        if (host.isBlank()) {
            throw new IllegalStateException("TENANT_DB_HOST must be set where tenant routing is on");
        }
        return new TenantPoolRegistry(registry, cipher, host, port, poolMax);
    }

    @Bean
    DataSource dataSource(TenantDataSources pools) {
        return new TenantRoutingDataSource(pools);
    }

    @Bean
    PlatformTransactionManager transactionManager(ObjectProvider<TransactionManagerCustomizers> customizers) {
        var transactions = new TenantAwareJpaTransactionManager();
        customizers.ifAvailable(customizer -> customizer.customize(transactions));
        return transactions;
    }

    /** No Workspace is bound at startup, so Hibernate must not ask a database about itself or its schema (R15). */
    @Bean
    HibernatePropertiesCustomizer noDatabaseAccessAtStartup() {
        return properties -> {
            properties.put("hibernate.boot.allow_jdbc_metadata_access", false);
            properties.put(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName());
            properties.put(AvailableSettings.HBM2DDL_AUTO, "none");
        };
    }

    /**
     * The tenant changelog keeps its own connection to {@code DB_*}. The url is always given: with a user and no url
     * Boot would build the migration connection from the router.
     */
    @Bean
    LiquibaseConnectionDetails tenantLiquibaseConnection(Environment environment) {
        return new LiquibaseConnectionDetails() {
            @Override
            public String getJdbcUrl() {
                return environment.getRequiredProperty("spring.datasource.url");
            }

            @Override
            public String getUsername() {
                return environment.getRequiredProperty("spring.datasource.username");
            }

            @Override
            public String getPassword() {
                return environment.getRequiredProperty("spring.datasource.password");
            }
        };
    }

    /** Takes the name of Boot's db health contributor, which would otherwise ask the router with nothing bound. */
    @Bean
    HealthIndicator dbHealthContributor(BeanFactory beanFactory) {
        return new DataSourceHealthIndicator(beanFactory.getBean("registryDataSource", DataSource.class));
    }
}
