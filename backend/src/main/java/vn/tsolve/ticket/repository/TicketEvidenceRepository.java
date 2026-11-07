package vn.tsolve.ticket.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import vn.tsolve.ticket.entity.TicketEvidence;

public interface TicketEvidenceRepository extends Repository<TicketEvidence, UUID> {
    List<TicketEvidence> findByTicketIdOrderById(UUID ticketId);
}
