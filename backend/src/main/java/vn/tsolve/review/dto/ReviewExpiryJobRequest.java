package vn.tsolve.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.JobRequest;
import vn.tsolve.review.service.impl.ReviewExpiryJobHandler;

public record ReviewExpiryJobRequest(UUID workspaceId, int attempt) implements JobRequest {
    @Override
    @JsonIgnore
    public Class<ReviewExpiryJobHandler> getJobRequestHandler() {
        return ReviewExpiryJobHandler.class;
    }
}
