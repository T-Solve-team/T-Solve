package vn.tsolve.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jobrunr.jobs.lambdas.JobRequest;
import vn.tsolve.review.service.impl.ReviewExpiryDispatchHandler;

public record ReviewExpiryDispatchRequest() implements JobRequest {
    @Override
    @JsonIgnore
    public Class<ReviewExpiryDispatchHandler> getJobRequestHandler() {
        return ReviewExpiryDispatchHandler.class;
    }
}
