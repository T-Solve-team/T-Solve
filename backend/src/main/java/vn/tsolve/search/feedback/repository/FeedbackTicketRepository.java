package vn.tsolve.search.feedback.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.search.scope.entity.SearchTicket;

public interface FeedbackTicketRepository extends JpaRepository<SearchTicket, UUID> {
    Optional<SearchTicket> findByPublicId(UUID publicId);
}
