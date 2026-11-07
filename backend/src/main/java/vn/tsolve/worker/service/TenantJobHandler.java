package vn.tsolve.worker.service;

import java.util.UUID;

/** Handlers must tolerate repeated delivery by JobRunr. */
public interface TenantJobHandler {
    String handlerType();

    void execute(UUID recordId);
}
