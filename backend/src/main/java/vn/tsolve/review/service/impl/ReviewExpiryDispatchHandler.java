package vn.tsolve.review.service.impl;

import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.review.dto.ReviewExpiryDispatchRequest;
import vn.tsolve.review.dto.ReviewExpiryJobRequest;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.service.WorkerTenantService;

@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class ReviewExpiryDispatchHandler implements JobRequestHandler<ReviewExpiryDispatchRequest> {
    private final WorkerTenantService tenants;
    private final JobRunrRuntime runtime;

    public ReviewExpiryDispatchHandler(WorkerTenantService tenants, JobRunrRuntime runtime) {
        this.tenants = tenants;
        this.runtime = runtime;
    }

    /**
     * Keeps JobRunr's default retries: a retry after a partial failure may enqueue a Workspace twice, which is
     * harmless because the expiry job is idempotent within one calendar day (AC-014.4).
     */
    @Override
    public void run(ReviewExpiryDispatchRequest request) {
        tenants.activeWorkspaceIds().forEach(id -> runtime.scheduler().enqueue(new ReviewExpiryJobRequest(id, 1)));
    }
}
