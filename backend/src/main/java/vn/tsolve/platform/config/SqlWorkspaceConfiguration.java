package vn.tsolve.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.common.tenant.ScopedTenantContext;
import vn.tsolve.common.tenant.TenantAccessException;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.TenantDataSource;
import vn.tsolve.common.tenant.TenantDatabase;
import vn.tsolve.platform.repository.SqlWorkspaceRepository;
import vn.tsolve.platform.service.WorkspaceCatalogService;
import vn.tsolve.platform.service.impl.WorkspaceCatalogServiceImpl;
import vn.tsolve.platform.web.WorkspaceHost;
import vn.tsolve.platform.web.WorkspaceTenantFilter;

@Configuration(proxyBeanMethods = false)
public class SqlWorkspaceConfiguration {
    @Bean
    FilterRegistrationBean<WorkspaceTenantFilter> sqlWorkspaceFilter(
            ObjectProvider<SqlWorkspaceResources> resources, ObjectMapper mapper) {
        var registration = new FilterRegistrationBean<>(new WorkspaceTenantFilter(resources::getIfAvailable, mapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "tsolve.sql-workspace.enabled", havingValue = "true")
    static class Enabled {
        @Bean(destroyMethod = "close")
        SqlWorkspaceResources sqlWorkspaceResources(Environment environment) {
            var properties = Binder.get(environment)
                    .bind("tsolve.sql-workspace", Bindable.of(SqlWorkspaceProperties.class))
                    .orElseThrow(() -> new IllegalStateException("SQL Workspace configuration is missing"));
            var hosts = new WorkspaceHost(properties.baseDomain());
            var catalog = properties.catalog();
            if (catalog == null
                    || blank(catalog.host())
                    || blank(catalog.database())
                    || blank(catalog.username())
                    || blank(catalog.password())
                    || catalog.port() < 1
                    || catalog.port() > 65535
                    || properties.poolMax() < 1
                    || properties.maxPools() < 1) {
                throw new IllegalStateException("SQL Workspace configuration is incomplete");
            }
            var catalogPool = pool(
                    "sql-catalog",
                    url(catalog.host(), catalog.port(), catalog.database()),
                    catalog.username(),
                    catalog.password(),
                    2);
            try {
                var repository = new SqlWorkspaceRepository(new JdbcTemplate(catalogPool));
                var service = new WorkspaceCatalogServiceImpl(repository);
                var transactions = new TransactionTemplate();
                var context =
                        new ScopedTenantContext(id -> repository.findById(id).isPresent(), transactions);
                var pools = new TenantPoolProvider(
                        repository::findConnection,
                        environment::getProperty,
                        (connection, password) -> pool(
                                "sql-tenant-" + connection.workspaceId(),
                                url(connection.host(), connection.port(), connection.database()),
                                connection.username(),
                                password,
                                properties.poolMax()),
                        properties.maxPools());
                var database = new TenantDatabase(new TenantDataSource(context, pools));
                transactions.setTransactionManager(database.newTransaction().getTransactionManager());
                transactions.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                return new SqlWorkspaceResources(catalogPool, pools, context, database, service, hosts);
            } catch (RuntimeException failure) {
                catalogPool.close();
                throw failure;
            }
        }

        @Bean
        TenantContext tenantContext(SqlWorkspaceResources resources) {
            return resources.context();
        }

        @Bean("sqlTenantDatabase")
        TenantDatabase sqlTenantDatabase(SqlWorkspaceResources resources) {
            return resources.database();
        }

        @Bean
        WorkspaceCatalogService workspaceCatalogService(SqlWorkspaceResources resources) {
            return resources.catalog();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String url(String host, int port, String database) {
        try {
            return "jdbc:" + new URI("postgresql", null, host, port, "/" + database, null, null).toASCIIString();
        } catch (URISyntaxException failure) {
            throw new TenantAccessException();
        }
    }

    private static HikariDataSource pool(String name, String url, String username, String password, int maxSize) {
        var config = new HikariConfig();
        config.setPoolName(name);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxSize);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(2000);
        return new HikariDataSource(config);
    }
}
