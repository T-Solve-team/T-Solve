package vn.tsolve.worker.config;

import java.util.UUID;
import org.jobrunr.server.JobActivator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.WebJobQueue;

/**
 * Lets the web process queue tenant jobs that the worker process runs (BUG-342), like the email outbox (BUG-339):
 * JobRunr only stores the job here, it runs no BackgroundJobServer. The Workspace is the one bound in the
 * {@link TenantContext}; nothing bound is refused. No JobRunrRuntime,
 * TenantJobService or WorkerTenantService bean is created: those tell other modules a request is a worker job.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "'${tsolve.worker.web-jobs-enabled:false}' == 'true' and '${tsolve.worker.enabled:false}' != 'true'")
public class WebJobsConfiguration {

    @Bean(destroyMethod = "close")
    WebJobs webJobQueue(
            TenantContext tenantContext,
            @Value("${tsolve.worker.jdbc-url:}") String jdbcUrl,
            @Value("${tsolve.worker.username:}") String username,
            @Value("${tsolve.worker.password:}") String password,
            @Value("${tsolve.worker.connection-timeout:3s}") String connectionTimeout) {
        if (jdbcUrl.isBlank()) {
            throw new IllegalStateException("tsolve.worker.jdbcUrl (JOBS_DB_URL) is required for web jobs");
        }
        return new WebJobs(
                new WorkerProperties(jdbcUrl, username, password, 1, DurationStyle.detectAndParse(connectionTimeout)),
                tenantContext);
    }

    /**
     * Creates JobRunr on the first job, not at start: the web start never waits on the Jobs database, and a failed
     * start is tried again by the next job.
     */
    static final class WebJobs implements WebJobQueue, AutoCloseable {
        private static final JobActivator NO_JOBS = new JobActivator() {
            @Override
            public <T> T activateJob(Class<T> type) {
                throw new IllegalStateException("The web process runs no jobs");
            }
        };

        private final WorkerProperties properties;
        private final TenantContext tenantContext;
        private JobRunrRuntime runtime;

        WebJobs(WorkerProperties properties, TenantContext tenantContext) {
            this.properties = properties;
            this.tenantContext = tenantContext;
        }

        @Override
        public UUID enqueue(String handlerType, UUID recordId) {
            // Throws NoTenantException when nothing is bound, before any JobRunr runtime exists (AC-002.16).
            UUID workspaceId = tenantContext.requireWorkspaceId();
            return runtime()
                    .scheduler()
                    .enqueue(new TenantJobRequest(workspaceId, handlerType, recordId))
                    .asUUID();
        }

        private synchronized JobRunrRuntime runtime() {
            if (runtime == null) {
                runtime = new JobRunrRuntime(properties, NO_JOBS, false);
            }
            return runtime;
        }

        @Override
        public synchronized void close() {
            if (runtime != null) {
                runtime.close();
            }
        }
    }
}
