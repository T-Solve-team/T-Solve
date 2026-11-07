package vn.tsolve.search.embedding.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface EmbeddingTicketRepository extends JpaRepository<SearchTicket, UUID> {
    List<SearchTicket> findByIdInAndStatus(List<UUID> ids, String status);
}
