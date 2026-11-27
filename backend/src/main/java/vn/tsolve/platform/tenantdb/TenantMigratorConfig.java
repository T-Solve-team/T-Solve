package vn.tsolve.platform.tenantdb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import vn.tsolve.platform.registry.PasswordCipher;
import vn.tsolve.platform.registry.RegistryRepository;

/** The {@link TenantMigrator} bean, where {@link TenantStartupMigrator} runs: the same gate. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${tsolve.registry.url:}'.isBlank()")
@Profile("!dev & !worker")
public class TenantMigratorConfig {

    @Bean
    TenantMigrator tenantMigrator(
            RegistryRepository registry,
            PasswordCipher cipher,
            @Value("${tsolve.tenant-db.host}") String host,
            @Value("${tsolve.tenant-db.port}") int port,
            @Value("${spring.liquibase.change-log}") String changeLog) {
        return new TenantMigrator(registry, cipher, host, port, changeLog);
    }
}
