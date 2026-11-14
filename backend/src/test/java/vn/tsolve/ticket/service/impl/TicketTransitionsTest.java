package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.event.TicketsUnpublishedEvent;

class TicketTransitionsTest {

    private static final Instant AT = Instant.parse("2026-11-13T00:00:00Z");

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TicketTransitions transitions = new TicketTransitions(events);

    @Test
    void aBatchWithOneForbiddenChangeChangesNoTicket_AC_005_3() {
        Ticket pending = ticket(1, TicketStatus.PENDING_REVIEW);
        Ticket rejected = ticket(2, TicketStatus.REJECTED);

        assertThatThrownBy(() -> transitions.move(List.of(pending, rejected), TicketStatus.PUBLISHED, AT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("MSG06");

        assertThat(pending.getStatus()).isEqualTo(TicketStatus.PENDING_REVIEW);
        assertThat(rejected.getStatus()).isEqualTo(TicketStatus.REJECTED);
        verifyNoInteractions(events);
    }

    @Test
    void movingToPublishedPublishesOneEventWithAllIds_AC_005_4() {
        Ticket a = ticket(1, TicketStatus.PENDING_REVIEW);
        Ticket b = ticket(2, TicketStatus.PENDING_REVIEW);

        List<UUID> ids = transitions.move(List.of(a, b), TicketStatus.PUBLISHED, AT);

        assertThat(ids).containsExactly(new UUID(0, 1), new UUID(0, 2));
        assertThat(a.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        assertThat(b.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        verify(events).publishEvent(new TicketsPublishedEvent(ids));
        verify(events, never()).publishEvent(any(TicketsUnpublishedEvent.class));
    }

    @Test
    void movingToUnpublishedPublishesOneEventWithAllIds_AC_005_5() {
        Ticket a = ticket(1, TicketStatus.PUBLISHED);
        Ticket b = ticket(2, TicketStatus.PUBLISHED);

        List<UUID> ids = transitions.move(List.of(a, b), TicketStatus.UNPUBLISHED, AT);

        assertThat(a.getStatus()).isEqualTo(TicketStatus.UNPUBLISHED);
        verify(events).publishEvent(new TicketsUnpublishedEvent(ids));
        verify(events, never()).publishEvent(any(TicketsPublishedEvent.class));
    }

    @Test
    void otherTargetsPublishNoEvent_AC_005_4() {
        transitions.move(List.of(ticket(1, TicketStatus.PENDING_REVIEW)), TicketStatus.REJECTED, AT);
        transitions.move(List.of(ticket(2, TicketStatus.PENDING_REVIEW)), TicketStatus.CHANGES_REQUESTED, AT);

        verifyNoInteractions(events);
    }

    private static Ticket ticket(long id, TicketStatus status) {
        Ticket t = Ticket.imported(
                "KAN-" + id,
                null,
                TicketSource.SOLUTION_FORM,
                null,
                "Title",
                null,
                null,
                null,
                new Ticket.Solution("s", "r", "steps", null, "scope"));
        ReflectionTestUtils.setField(t, "id", new UUID(0, id));
        ReflectionTestUtils.setField(t, "status", status);
        return t;
    }
}
