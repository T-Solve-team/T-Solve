package vn.tsolve.worker.service;

import java.util.UUID;

public interface TenantJobService {
    UUID enqueue(String handlerType, UUID recordId);
}
