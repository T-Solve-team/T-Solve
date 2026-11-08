package vn.tsolve.review.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.tsolve.review.dto.ReviewExpiryJobRequest;
import vn.tsolve.review.service.ReviewExpiryService;
import vn.tsolve.worker.config.JobRunrRuntime;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.WorkerTenantService;

@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class ReviewExpiryJobHandler implements JobRequestHandler<ReviewExpiryJobRequest> {
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration RETRY_DELAY = Duration.ofMinutes(15);
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger log = LoggerFactory.getLogger(ReviewExpiryJobHandler.class);
    private final WorkerTenantService tenants;
    private final TenantExecutionService execution;
    private final ReviewExpiryService expiry;
    private final JobRunrRuntime runtime;
    private final Clock clock;

    public ReviewExpiryJobHandler(
            WorkerTenantService tenants,
            TenantExecutionService execution,
            ReviewExpiryService expiry,
            JobRunrRuntime runtime,
            Clock clock) {
        this.tenants = tenants;
        this.execution = execution;
        this.expiry = expiry;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    @Job(name = "JOB-02 expire overdue tickets", retries = 0)
    public void run(ReviewExpiryJobRequest request) {
        UUID workspace = request.workspaceId();
        if (!tenants.isWorkspaceActive(workspace)) {
            log.info("JOB-02 skipped inactive Workspace {}", workspace);
            return;
        }
        try {
            LocalDate today = LocalDate.now(clock.withZone(ZONE));
            execution.runInTenant(workspace, () -> expiry.expireOverdue(today));
        } catch (RuntimeException failure) {
            log.warn(
                    "JOB-02 attempt {} failed in Workspace {} with {}",
                    request.attempt(),
                    workspace,
                    failure.getClass().getName());
            if (request.attempt() < MAX_ATTEMPTS) {
                runtime.scheduler()
                        .schedule(
                                clock.instant().plus(RETRY_DELAY),
                                new ReviewExpiryJobRequest(workspace, request.attempt() + 1));
            } else {
                log.warn(
                        "JOB-02 no further attempt scheduled for Workspace {} after attempt {}",
                        workspace,
                        request.attempt());
            }
        }
    }
}
