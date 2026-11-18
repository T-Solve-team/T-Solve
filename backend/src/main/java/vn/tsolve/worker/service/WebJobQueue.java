package vn.tsolve.worker.service;

import java.util.UUID;

/**
 * Queues a tenant job from a web process that runs no JobRunr; the worker process runs it in the job's Workspace
 * (BUG-342). Not a {@link TenantJobService}: that bean would switch other modules, such as the Wiki, to the worker
 * path.
 */
@FunctionalInterface
public interface WebJobQueue {
    /** Returns the job id; throws when there is no single Workspace or the Jobs database cannot be reached. */
    UUID enqueue(String handlerType, UUID recordId);
}
