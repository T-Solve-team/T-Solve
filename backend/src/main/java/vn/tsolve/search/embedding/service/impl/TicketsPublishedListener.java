package vn.tsolve.search.embedding.service.impl;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;
import vn.tsolve.ticket.event.TicketsPublishedEvent;

@Component
public class TicketsPublishedListener {
    private static final Logger log = LoggerFactory.getLogger(TicketsPublishedListener.class);

    private final TicketEmbeddingService service;
    private final TaskExecutor executor;
    private final TenantContext tenants;

    public TicketsPublishedListener(
            TicketEmbeddingService service,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor,
            TenantContext tenants) {
        this.service = service;
        this.executor = executor;
        this.tenants = tenants;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketsPublishedEvent event) {
        // Read on the publishing thread: the executor thread starts unbound.
        Optional<UUID> workspace = tenants.currentWorkspaceId();
        try {
            executor.execute(() -> {
                try {
                    Runnable embed = () -> service.embed(event.ticketIds());
                    workspace.ifPresentOrElse(id -> tenants.runIn(id, embed), embed);
                } catch (RuntimeException exception) {
                    log.warn(
                            "embedding failed for tickets {} cause={}",
                            event.ticketIds(),
                            exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException exception) {
            log.warn(
                    "embedding dispatch rejected for tickets {} cause={}",
                    event.ticketIds(),
                    exception.getClass().getSimpleName());
        }
    }
}
