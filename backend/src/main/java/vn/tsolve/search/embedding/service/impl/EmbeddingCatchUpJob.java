package vn.tsolve.search.embedding.service.impl;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.platform.dto.WorkspaceSummary;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.search.embedding.repository.TicketEmbeddingRepository;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;

@Component
@ConditionalOnProperty(
        prefix = "tsolve.search.embedding",
        name = "catch-up-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmbeddingCatchUpJob {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingCatchUpJob.class);

    private final TicketEmbeddingRepository repository;
    private final TicketEmbeddingService service;
    private final int batchSize;
    private final ObjectProvider<WorkspaceCatalog> catalog;
    private final TenantContext tenants;

    public EmbeddingCatchUpJob(
            TicketEmbeddingRepository repository,
            TicketEmbeddingService service,
            @Value("${SEARCH_EMBED_BATCH:4}") int batchSize,
            ObjectProvider<WorkspaceCatalog> catalog,
            TenantContext tenants) {
        this.repository = repository;
        this.service = service;
        this.batchSize = batchSize;
        this.catalog = catalog;
        this.tenants = tenants;
    }

    // Spring 6.1's scheduler needs milliseconds; Boot's parser also accepts settings such as 60s.
    @Scheduled(
            fixedDelayString = "#{T(org.springframework.boot.convert.DurationStyle)"
                    + ".detectAndParse('${SEARCH_EMBED_CATCH_UP_DELAY:60s}').toMillis()}")
    public void run() {
        WorkspaceCatalog workspaces = catalog.getIfAvailable();
        if (workspaces == null) {
            embedMissing();
            return;
        }
        for (WorkspaceSummary workspace : workspaces.findActive()) {
            try {
                tenants.runIn(workspace.id(), this::embedMissing);
            } catch (RuntimeException failure) {
                // The message may hold ids or ticket content.
                log.warn(
                        "embedding catch-up failed in one Workspace cause={}",
                        failure.getClass().getSimpleName());
            }
        }
    }

    private void embedMissing() {
        List<UUID> ids = repository.findPublishedWithoutEmbedding(
                batchSize, service.skippedIds().toArray(UUID[]::new));
        service.embed(ids);
    }
}
