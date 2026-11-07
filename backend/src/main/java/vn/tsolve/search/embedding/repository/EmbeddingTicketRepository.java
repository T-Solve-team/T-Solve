package vn.tsolve.search.embedding.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface EmbeddingTicketRepository extends JpaRepository<SearchTicket, Long> {
    List<SearchTicket> findByIdInAndStatus(List<Long> ids, String status);
}
