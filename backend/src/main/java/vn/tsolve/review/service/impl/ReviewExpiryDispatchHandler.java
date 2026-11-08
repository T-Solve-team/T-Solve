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

    @Override
    public void run(ReviewExpiryDispatchRequest request) {
        tenants.activeWorkspaceIds().forEach(id -> runtime.scheduler().enqueue(new ReviewExpiryJobRequest(id, 1)));
    }
}
