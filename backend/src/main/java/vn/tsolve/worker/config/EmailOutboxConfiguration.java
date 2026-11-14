package vn.tsolve.worker.config;

import java.time.Clock;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.impl.EmailOutboxServiceImpl;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EmailOutboxProperties.class)
public class EmailOutboxConfiguration {
    @Bean
    static BeanFactoryPostProcessor jobsLiquibaseDefinition() {
        return factory -> {
            // Register after auto-configuration conditions: a regular SpringLiquibase @Bean would
            // make Boot back off from the existing tenant migration (@ConditionalOnMissingBean).
            var definition = new RootBeanDefinition(SpringLiquibase.class);
            definition.setInstanceSupplier(() -> {
                var migration = new SpringLiquibase();
                migration.setDataSource(factory.getBean(JobRunrRuntime.class).jobsDataSource());
                migration.setChangeLog("classpath:db/jobs-changelog/db.changelog-master.yaml");
                return migration;
            });
            ((BeanDefinitionRegistry) factory).registerBeanDefinition("jobsLiquibase", definition);
        };
    }

    @Bean
    Clock workerClock() {
        return Clock.systemUTC();
    }

    @Bean
    @DependsOn("jobsLiquibase")
    EmailOutboxRepository emailOutboxRepository(JobRunrRuntime runtime) {
        return new EmailOutboxRepository(runtime.jobsDataSource());
    }

    @Bean
    EmailOutboxService emailOutboxService(
            EmailOutboxRepository repository, WorkerTenantService tenants, @Qualifier("workerClock") Clock clock) {
        return new EmailOutboxServiceImpl(repository, tenants, clock);
    }
}
