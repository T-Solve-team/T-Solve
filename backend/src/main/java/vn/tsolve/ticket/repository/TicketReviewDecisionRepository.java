package vn.tsolve.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.TicketReviewDecision;

public interface TicketReviewDecisionRepository extends JpaRepository<TicketReviewDecision, Long> {}
