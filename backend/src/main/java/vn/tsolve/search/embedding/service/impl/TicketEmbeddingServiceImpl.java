package vn.tsolve.search.embedding.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.search.embedding.repository.EmbeddingTicketRepository;
import vn.tsolve.search.embedding.repository.PgVector;
import vn.tsolve.search.embedding.repository.TicketEmbeddingRepository;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;
import vn.tsolve.search.scope.entity.SearchTicket;

@Service
public class TicketEmbeddingServiceImpl implements TicketEmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(TicketEmbeddingServiceImpl.class);
    private static final int MAX_FAILURES = 3;

    private final EmbeddingTicketRepository tickets;
    private final TicketEmbeddingRepository embeddings;
    private final EmbeddingClient client;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    private final TransactionTemplate outsideTransaction;
    private final int batchSize;
    private final String model;
    private final Map<Long, Integer> failures = new HashMap<>();

    public TicketEmbeddingServiceImpl(
            EmbeddingTicketRepository tickets,
            TicketEmbeddingRepository embeddings,
            EmbeddingClient client,
            PlatformTransactionManager transactionManager,
            @Value("${SEARCH_EMBED_BATCH:4}") int batchSize,
            @Value("${OLLAMA_EMBED_MODEL:bge-m3}") String model) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("SEARCH_EMBED_BATCH must be positive");
        }
        this.tickets = tickets;
        this.embeddings = embeddings;
        this.client = client;
        this.batchSize = batchSize;
        this.model = model;
        read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write = new TransactionTemplate(transactionManager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outsideTransaction = new TransactionTemplate(transactionManager);
        outsideTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public void embed(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        for (int start = 0; start < distinct.size(); start += batchSize) {
            embedBatch(distinct.subList(start, Math.min(start + batchSize, distinct.size())));
        }
    }

    @Override
    public synchronized Set<Long> skippedIds() {
        return failures.entrySet().stream()
                .filter(entry -> entry.getValue() >= MAX_FAILURES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ponytail: this lock is per process; each API replica runs its own batch (one replica today).
    private synchronized void embedBatch(List<Long> ids) {
        List<Long> attempted = ids;
        try {
            List<SearchTicket> published = read.execute(status -> tickets.findByIdInAndStatus(ids, "PUBLISHED"));
            if (published.isEmpty()) {
                return;
            }
            attempted = published.stream().map(SearchTicket::getId).toList();
            List<String> texts = published.stream().map(EmbeddingText::of).toList();
            List<float[]> vectors = outsideTransaction.execute(status -> client.embed(texts));
            if (vectors.size() != published.size()
                    || vectors.stream().anyMatch(vector -> vector.length != EmbeddingClient.DIMENSIONS)) {
                throw new IllegalStateException("Invalid embedding dimensions or count");
            }
            write.executeWithoutResult(status -> {
                for (int index = 0; index < published.size(); index++) {
                    embeddings.upsert(published.get(index).getId(), PgVector.literal(vectors.get(index)), model);
                }
            });
            attempted.forEach(failures::remove);
        } catch (RuntimeException exception) {
            log.warn(
                    "embedding failed for tickets {} cause={}",
                    attempted,
                    exception.getClass().getSimpleName());
            for (Long id : attempted) {
                int previous = failures.getOrDefault(id, 0);
                failures.put(id, Math.min(previous + 1, MAX_FAILURES));
                if (previous == MAX_FAILURES - 1) {
                    log.warn("skipping embedding for ticket {}", id);
                }
            }
        }
    }
}
