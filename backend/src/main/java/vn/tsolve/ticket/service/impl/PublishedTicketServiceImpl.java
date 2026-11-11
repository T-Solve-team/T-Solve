package vn.tsolve.ticket.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.PublishedTicketView;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.repository.TicketRepository;
import vn.tsolve.ticket.repository.TicketRepository.PublishedTicketRow;
import vn.tsolve.ticket.service.PublishedTicketService;

@Service
public class PublishedTicketServiceImpl implements PublishedTicketService {

    private final TicketRepository tickets;

    public PublishedTicketServiceImpl(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishedTicketView> findPublished(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return tickets.findPublished(ids).stream()
                .map(PublishedTicketServiceImpl::view)
                .toList();
    }

    private static PublishedTicketView view(PublishedTicketRow row) {
        return new PublishedTicketView(
                row.getId(),
                row.getTicketId(),
                row.getTitle(),
                row.getDepartmentId(),
                row.getDepartmentName(),
                new SolutionSections(
                        row.getSymptoms(),
                        row.getRootCause(),
                        row.getResolutionSteps(),
                        row.getWorkaround(),
                        row.getScope()));
    }
}
