package vn.tsolve.ticket;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.NotFoundException;

@Service
public class TicketService {

    private final ResolvedTicketRepository repository;

    public TicketService(ResolvedTicketRepository repository) {
        this.repository = repository;
    }

    /** Idempotent import: the same source + externalId updates the existing snapshot instead of duplicating it. */
    @Transactional
    public ResolvedTicketSnapshot importTicket(ImportTicketRequest r) {
        ResolvedTicketSnapshot snapshot = repository
                .findBySourceAndExternalId(r.source(), r.externalId())
                .orElseGet(() -> new ResolvedTicketSnapshot(r.workspaceId(), r.source(), r.externalId()));
        snapshot.update(r);
        return repository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<ResolvedTicketSnapshot> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public ResolvedTicketSnapshot get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Ticket snapshot " + id + " not found"));
    }
}
