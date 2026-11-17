package vn.tsolve.worker.config;

import java.util.List;
import org.jobrunr.server.JobActivator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.TenantJobHandler;
import vn.tsolve.worker.service.TenantJobService;
import vn.tsolve.worker.service.WorkerTenantService;
import vn.tsolve.worker.service.impl.TenantExecutionServiceImpl;
import vn.tsolve.worker.service.impl.TenantJobRequestHandler;
import vn.tsolve.worker.service.impl.TenantJobServiceImpl;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfiguration {
    private final WorkerTenantService tenants;

    public WorkerConfiguration(WorkerTenantService tenants) {
        this.tenants = tenants;
    }

    @Bean
    TenantExecutionService tenantExecutionService() {
        return new TenantExecutionServiceImpl(tenants);
    }

    @Bean
    TenantJobRequestHandler tenantJobRequestHandler(TenantExecutionService execution, List<TenantJobHandler> handlers) {
        return new TenantJobRequestHandler(execution, handlers);
    }

    @Bean(destroyMethod = "close")
    JobRunrRuntime jobRunrRuntime(
            WorkerProperties properties,
            ApplicationContext context,
            @Value("${jobrunr.background-job-server.enabled:false}") boolean processJobs) {
        JobActivator activator = new JobActivator() {
            @Override
            public <T> T activateJob(Class<T> type) {
                return context.getBean(type);
            }
        };
        return new JobRunrRuntime(properties, activator, processJobs);
    }

    @Bean
    TenantJobService tenantJobService(JobRunrRuntime runtime, TenantJobRequestHandler handler) {
        return new TenantJobServiceImpl(tenants, runtime.scheduler(), handler);
    }
}
