package vn.tsolve.worker.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import vn.tsolve.worker.dto.TenantJobRequest;
import vn.tsolve.worker.service.TenantExecutionService;
import vn.tsolve.worker.service.TenantJobHandler;

public class TenantJobRequestHandler implements JobRequestHandler<TenantJobRequest> {
    private final TenantExecutionService execution;
    private final Map<String, TenantJobHandler> handlers;

    public TenantJobRequestHandler(TenantExecutionService execution, List<TenantJobHandler> handlers) {
        this.execution = execution;
        Map<String, TenantJobHandler> registered = new HashMap<>();
        for (TenantJobHandler handler : handlers) {
            String type = handler.handlerType();
            if (type == null || type.isBlank() || registered.putIfAbsent(type, handler) != null) {
                throw new IllegalArgumentException("Job handler types must be nonblank and unique");
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    @Override
    public void run(TenantJobRequest request) {
        Objects.requireNonNull(request, "request");
        TenantJobHandler handler = requireHandler(request.handlerType(), request.recordId());
        execution.runInTenant(request.workspaceId(), () -> handler.execute(request.recordId()));
    }

    TenantJobHandler requireHandler(String type, UUID recordId) {
        Objects.requireNonNull(recordId, "recordId");
        TenantJobHandler handler = type == null ? null : handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown job handler type");
        }
        return handler;
    }
}
