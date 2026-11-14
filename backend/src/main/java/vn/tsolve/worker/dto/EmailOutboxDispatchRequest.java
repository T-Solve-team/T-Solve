package vn.tsolve.worker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jobrunr.jobs.lambdas.JobRequest;
import vn.tsolve.worker.service.impl.EmailOutboxDispatchHandler;

public record EmailOutboxDispatchRequest() implements JobRequest {
    @Override
    @JsonIgnore
    public Class<EmailOutboxDispatchHandler> getJobRequestHandler() {
        return EmailOutboxDispatchHandler.class;
    }
}
