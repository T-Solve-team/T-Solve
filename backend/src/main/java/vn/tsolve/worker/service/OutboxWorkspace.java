package vn.tsolve.worker.service;

import java.util.Optional;
import java.util.UUID;

/**
 * The Workspace a web process queues mail for, when it runs only the email outbox (BUG-339, BUG-340). Not a
 * {@link WorkerTenantService}: that bean tells other modules a request is a worker job.
 */
@FunctionalInterface
public interface OutboxWorkspace {
    Optional<UUID> current();
}
