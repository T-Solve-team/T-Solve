package vn.tsolve.platform.tenantdb;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Runs {@link TenantMigrator#applyAll()} while the web context starts, after the Registry changelog. A failure stops the
 * start, so the process never reports healthy (AC-008.9). Where it runs, Boot's tenant Liquibase does not exist, and
 * the routing {@code DataSource} is created only after every database is migrated (R14).
 */
@Component
@ConditionalOnExpression("!'${tsolve.registry.url:}'.isBlank()")
@Profile("!dev & !worker")
@DependsOn("registryLiquibase")
public class TenantStartupMigrator {

    public TenantStartupMigrator(TenantMigrator migrator) {
        migrator.applyAll();
    }

    /** Runs before JPA is told to wait for Boot's {@code liquibase} bean, so removing that bean leaves no reference. */
    @Bean
    static BeanDefinitionRegistryPostProcessor startupMigrationReplacesBootLiquibase() {
        return registry -> {
            // Boot would run it on the router, with no Workspace bound.
            if (registry.containsBeanDefinition("liquibase")) {
                registry.removeBeanDefinition("liquibase");
            }
            var dataSource = registry.getBeanDefinition("dataSource");
            dataSource.setDependsOn(StringUtils.concatenateStringArrays(
                    dataSource.getDependsOn(),
                    ((ListableBeanFactory) registry).getBeanNamesForType(TenantStartupMigrator.class, false, false)));
        };
    }
}
