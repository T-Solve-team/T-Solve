package vn.tsolve.ticket.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.ReviewDecision;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {
    List<ReviewDecision> findByTicketIdOrderByDecidedAtDescIdDesc(long ticketId);
}
