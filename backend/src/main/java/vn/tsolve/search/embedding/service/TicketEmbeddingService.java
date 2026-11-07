package vn.tsolve.search.embedding.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TicketEmbeddingService {
    void embed(List<UUID> ids);

    Set<UUID> skippedIds();
}
