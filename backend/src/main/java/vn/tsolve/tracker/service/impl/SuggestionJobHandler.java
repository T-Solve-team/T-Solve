package vn.tsolve.tracker.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.tsolve.tracker.service.SuggestionJobService;
import vn.tsolve.worker.service.TenantJobHandler;

/**
 * Runs the suggestion job for the worker (KAN-102): the worker calls it inside the job's Workspace with the
 * suggestion result's job reference. A failure propagates so JobRunr retries the job (AC-011.6); reruns are safe
 * because the job skips a result that is already filled (AC-011.7).
 */
@Component
public class SuggestionJobHandler implements TenantJobHandler {

    static final String TYPE = "tracker.suggestion";

    private final SuggestionJobService jobService;

    public SuggestionJobHandler(SuggestionJobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public String handlerType() {
        return TYPE;
    }

    @Override
    public void execute(UUID recordId) {
        jobService.run(recordId);
    }
}
