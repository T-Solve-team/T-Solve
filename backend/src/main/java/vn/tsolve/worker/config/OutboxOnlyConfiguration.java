package vn.tsolve.worker.config;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.impl.EmailOutboxServiceImpl;

/**
 * Lets the web process write to the Jobs email outbox without the worker runtime (BUG-339). The review decision is
 * taken in the web process; the worker process only sends. Unlike {@code tsolve.worker.enabled} this starts no
 * JobRunr, registers no job handler and does not change the tracker queue, and it needs no worker profile; the
 * Workspace comes from the {@link TenantContext} holder.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "'${tsolve.worker.outbox-enabled:false}' == 'true' and '${tsolve.worker.enabled:false}' != 'true'")
public class OutboxOnlyConfiguration {

    /** The private Jobs pool; deliberately not a DataSource bean, so Spring cannot pick it as the business pool. */
    static final class JobsPool implements AutoCloseable {
        private final HikariDataSource pool;

        JobsPool(HikariDataSource pool) {
            this.pool = pool;
        }

        DataSource dataSource() {
            return pool;
        }

        @Override
        public void close() {
            pool.close();
        }
    }

    @Bean(destroyMethod = "close")
    JobsPool outboxJobsPool(
            @Value("${tsolve.worker.jdbc-url:}") String jdbcUrl,
            @Value("${tsolve.worker.username:}") String username,
            @Value("${tsolve.worker.password:}") String password,
            @Value("${tsolve.worker.connection-timeout:3s}") String connectionTimeout) {
        if (jdbcUrl.isBlank()) {
            // No fallback to the business DataSource.
            throw new IllegalStateException("tsolve.worker.jdbcUrl (JOBS_DB_URL) is required for the email outbox");
        }
        var config = JobRunrRuntime.poolConfig(
                new WorkerProperties(jdbcUrl, username, password, 1, DurationStyle.detectAndParse(connectionTimeout)));
        // Do not block or fail the web start when the Jobs database is slow; the migration below still needs it.
        config.setInitializationFailTimeout(-1);
        return new JobsPool(new HikariDataSource(config));
    }

    @Bean
    static BeanFactoryPostProcessor jobsLiquibaseDefinition() {
        return factory -> {
            // Registered after auto-configuration conditions: a regular SpringLiquibase @Bean would make Boot back
            // off from the tenant migration (@ConditionalOnMissingBean). Same approach as EmailOutboxConfiguration.
            var definition = new RootBeanDefinition(SpringLiquibase.class);
            definition.setInstanceSupplier(() -> {
                var migration = new SpringLiquibase();
                migration.setDataSource(factory.getBean(JobsPool.class).dataSource());
                migration.setChangeLog("classpath:db/jobs-changelog/db.changelog-master.yaml");
                return migration;
            });
            ((BeanDefinitionRegistry) factory).registerBeanDefinition("jobsLiquibase", definition);
        };
    }

    @Bean
    @DependsOn("jobsLiquibase")
    EmailOutboxRepository emailOutboxRepository(JobsPool pool) {
        return new EmailOutboxRepository(pool.dataSource());
    }

    /** The outbox queues for the Workspace bound in the {@link TenantContext}; nothing bound is refused (AC-002.16). */
    @Bean
    EmailOutboxService emailOutboxService(EmailOutboxRepository repository, TenantContext tenantContext) {
        return new EmailOutboxServiceImpl(repository, new RequestTenant(tenantContext), Clock.systemUTC());
    }

    /**
     * The outbox only asks for the bound Workspace. Not a bean: a {@link WorkerTenantService} bean would tell
     * WorkspaceJiraServiceAccount and the review expiry that a web request is a worker job.
     */
    private record RequestTenant(TenantContext tenantContext) implements WorkerTenantService {
        @Override
        public Optional<UUID> currentWorkspaceId() {
            // Throws NoTenantException when nothing is bound.
            return Optional.of(tenantContext.requireWorkspaceId());
        }

        @Override
        public Optional<UUID> enqueueWorkspaceId() {
            return Optional.empty();
        }

        @Override
        public void runIn(UUID workspaceId, Runnable action) {
            throw new UnsupportedOperationException("The web outbox does not bind Workspaces");
        }

        @Override
        public boolean isWorkspaceActive(UUID workspaceId) {
            throw new UnsupportedOperationException("The web outbox does not check Workspace status");
        }

        @Override
        public Set<UUID> activeWorkspaceIds() {
            throw new UnsupportedOperationException("The web outbox does not list Workspaces");
        }
    }
}
