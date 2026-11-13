package vn.tsolve.ticket.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.event.TicketsUnpublishedEvent;

/** The one way a request changes Ticket statuses (STORY-005): all of the batch or none, then one event. */
@Component
public class TicketTransitions {

    private final ApplicationEventPublisher events;

    public TicketTransitions(ApplicationEventPublisher events) {
        this.events = events;
    }

    /** Moves every ticket to {@code to}, or throws MSG06 and changes none (D11). Returns the tickets' ids. */
    public List<UUID> move(List<Ticket> tickets, TicketStatus to, Instant at) {
        if (tickets.stream().anyMatch(ticket -> !ticket.getStatus().canMoveTo(to))) {
            throw new BusinessRuleException("MSG06", Map.of());
        }
        tickets.forEach(ticket -> ticket.moveTo(to, at));
        List<UUID> ids = tickets.stream().map(Ticket::getId).toList();
        if (!ids.isEmpty() && to == TicketStatus.PUBLISHED) {
            events.publishEvent(new TicketsPublishedEvent(ids));
        } else if (!ids.isEmpty() && to == TicketStatus.UNPUBLISHED) {
            events.publishEvent(new TicketsUnpublishedEvent(ids));
        }
        return ids;
    }
}
