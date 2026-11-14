package vn.tsolve.ticket.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.TicketEvidence;

public interface TicketEvidenceRepository extends JpaRepository<TicketEvidence, UUID> {
    List<TicketEvidence> findByTicketIdOrderById(UUID ticketId);
}
