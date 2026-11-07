package vn.tsolve.worker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.JobRequest;
import vn.tsolve.worker.service.impl.TenantJobRequestHandler;

public record TenantJobRequest(UUID workspaceId, String handlerType, UUID recordId) implements JobRequest {
    @Override
    @JsonIgnore
    public Class<TenantJobRequestHandler> getJobRequestHandler() {
        return TenantJobRequestHandler.class;
    }
}
