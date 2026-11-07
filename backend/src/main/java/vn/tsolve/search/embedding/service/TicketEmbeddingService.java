package vn.tsolve.search.embedding.service;

import java.util.List;
import java.util.Set;

public interface TicketEmbeddingService {
    void embed(List<Long> ids);

    Set<Long> skippedIds();
}
