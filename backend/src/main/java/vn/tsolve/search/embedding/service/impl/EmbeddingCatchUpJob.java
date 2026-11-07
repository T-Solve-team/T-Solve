package vn.tsolve.search.embedding.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.tsolve.search.embedding.repository.TicketEmbeddingRepository;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;

@Component
public class EmbeddingCatchUpJob {
    private final TicketEmbeddingRepository repository;
    private final TicketEmbeddingService service;
    private final int batchSize;

    public EmbeddingCatchUpJob(
            TicketEmbeddingRepository repository,
            TicketEmbeddingService service,
            @Value("${SEARCH_EMBED_BATCH:4}") int batchSize) {
        this.repository = repository;
        this.service = service;
        this.batchSize = batchSize;
    }

    // Spring 6.1's scheduler needs milliseconds; Boot's parser also accepts settings such as 60s.
    @Scheduled(
            fixedDelayString = "#{T(org.springframework.boot.convert.DurationStyle)"
                    + ".detectAndParse('${SEARCH_EMBED_CATCH_UP_DELAY:60s}').toMillis()}")
    public void run() {
        List<Long> ids = repository.findPublishedWithoutEmbedding(
                batchSize, service.skippedIds().toArray(Long[]::new));
        service.embed(ids);
    }
}
