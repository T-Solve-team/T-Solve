package vn.tsolve.ticket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.ticket.entity.Ticket;
import vn.tsolve.ticket.entity.TicketDecision;
import vn.tsolve.ticket.entity.TicketSource;
import vn.tsolve.ticket.entity.TicketStatus;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.ticket.event.TicketsPublishedEvent;
import vn.tsolve.ticket.event.TicketsUnpublishedEvent;
import vn.tsolve.ticket.exception.ProjectAccessDeniedException;
import vn.tsolve.ticket.repository.TicketDecisionRepository;
import vn.tsolve.ticket.repository.TicketRepository;

/** Unpublish / re-publish (STORY-009, KAN-136). */
class TicketPublicationTest {

    private static final Instant NOW = Instant.parse("2026-11-15T03:00:00Z");
    private static final UUID REVIEWER = new UUID(9, 1);
    private static final UUID PROJECT = new UUID(9, 2);
    private static final UUID OTHER_PROJECT = new UUID(9, 3);
    private static final LocalDate DUE = LocalDate.of(2026, 11, 1);

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketDecisionRepository decisions = mock(TicketDecisionRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditService audit = mock(AuditService.class);
    private final TicketDecisionServiceImpl service = new TicketDecisionServiceImpl(
            tickets, decisions, Clock.fixed(NOW, ZoneOffset.UTC), events, audit, new TicketTransitions(events));

    @Test
    void unpublishChangesStatusStoresDecisionAndAudit_AC_009_1() {
        Ticket a = stored(1, TicketStatus.PUBLISHED, PROJECT);
        Ticket b = stored(2, TicketStatus.PUBLISHED, PROJECT);

        int count = service.unpublish(REVIEWER, Set.of(PROJECT), List.of(a.getId(), b.getId()));

        assertThat(count).isEqualTo(2);
        assertThat(List.of(a, b)).extracting(Ticket::getStatus).containsOnly(TicketStatus.UNPUBLISHED);
        assertThat(savedDecisions())
                .extracting("ticketId", "reviewerId", "decision", "feedback", "decidedAt")
                .containsExactly(
                        tuple(a.getId(), REVIEWER, TicketDecision.Decision.UNPUBLISH, null, NOW),
                        tuple(b.getId(), REVIEWER, TicketDecision.Decision.UNPUBLISH, null, NOW));
        verify(audit).record(AuditAction.UNPUBLISH, "TICKET", a.getId());
        verify(audit).record(AuditAction.UNPUBLISH, "TICKET", b.getId());
        verify(events).publishEvent(new TicketsUnpublishedEvent(List.of(a.getId(), b.getId())));
        verify(events, never()).publishEvent(any(ReviewFeedbackDecidedEvent.class));
    }

    @Test
    void republishPublishesAgainAndKeepsEverythingElse_AC_009_2() {
        Ticket ticket = stored(1, TicketStatus.UNPUBLISHED, PROJECT);
        ReflectionTestUtils.setField(ticket, "rejectionReason", "Old reason");
        ReflectionTestUtils.setField(ticket, "changeRequestComment", "Old comment");

        int count = service.republish(REVIEWER, Set.of(PROJECT), List.of(ticket.getId()));

        assertThat(count).isEqualTo(1);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PUBLISHED);
        assertThat(ticket.getReviewDueDate()).isEqualTo(DUE);
        assertThat(ticket.getRejectionReason()).isEqualTo("Old reason");
        assertThat(ticket.getChangeRequestComment()).isEqualTo("Old comment");
        assertThat(ticket).extracting("symptoms", "resolutionSteps").containsExactly("s", "steps");
        assertThat(savedDecisions())
                .extracting("ticketId", "reviewerId", "decision", "feedback", "decidedAt")
                .containsExactly(tuple(ticket.getId(), REVIEWER, TicketDecision.Decision.REPUBLISH, null, NOW));
        verify(audit).record(AuditAction.PUBLISH, "TICKET", ticket.getId());
        verify(events).publishEvent(new TicketsPublishedEvent(List.of(ticket.getId())));
        verify(events, never()).publishEvent(any(ReviewFeedbackDecidedEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void unpublishRefusesAMixedBatchWithAnyOtherStatus_AC_009_3(TicketStatus wrong) {
        Ticket published = stored(1, TicketStatus.PUBLISHED, PROJECT);
        Ticket other = stored(2, wrong, PROJECT);

        assertThatThrownBy(
                        () -> service.unpublish(REVIEWER, Set.of(PROJECT), List.of(published.getId(), other.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("MSG06");

        assertNothingChanged(published, TicketStatus.PUBLISHED);
        assertThat(other.getStatus()).isEqualTo(wrong);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = "UNPUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void republishRefusesAMixedBatchWithAnyOtherStatus_AC_009_3(TicketStatus wrong) {
        Ticket unpublished = stored(1, TicketStatus.UNPUBLISHED, PROJECT);
        Ticket other = stored(2, wrong, PROJECT);

        assertThatThrownBy(
                        () -> service.republish(REVIEWER, Set.of(PROJECT), List.of(unpublished.getId(), other.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("MSG06");

        assertNothingChanged(unpublished, TicketStatus.UNPUBLISHED);
        assertThat(other.getStatus()).isEqualTo(wrong);
    }

    @Test
    void anIdOutsideTheWorkspaceIsNotFound_AC_009_4() {
        Ticket published = stored(1, TicketStatus.PUBLISHED, PROJECT);

        assertThatThrownBy(
                        () -> service.unpublish(REVIEWER, Set.of(PROJECT), List.of(published.getId(), new UUID(0, 99))))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("MSG04");

        assertNothingChanged(published, TicketStatus.PUBLISHED);
    }

    @Test
    void aTicketOfAProjectTheCallerDoesNotManageIsForbidden_AC_009_5() {
        Ticket mine = stored(1, TicketStatus.UNPUBLISHED, PROJECT);
        Ticket theirs = stored(2, TicketStatus.UNPUBLISHED, OTHER_PROJECT);

        assertThatThrownBy(() -> service.republish(REVIEWER, Set.of(PROJECT), List.of(mine.getId(), theirs.getId())))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .extracting("code")
                .isEqualTo("MSG08");

        assertNothingChanged(mine, TicketStatus.UNPUBLISHED);
    }

    @Test
    void noIdTooManyIdsOrADuplicateIdIsABadRequest_AC_009_7() {
        Ticket published = stored(1, TicketStatus.PUBLISHED, PROJECT);
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            tooMany.add(new UUID(1, i));
        }

        for (List<UUID> ids :
                Arrays.asList(null, List.<UUID>of(), tooMany, List.of(published.getId(), published.getId()))) {
            assertThatThrownBy(() -> service.unpublish(REVIEWER, Set.of(PROJECT), ids))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode.value")
                    .isEqualTo(400);
            assertThatThrownBy(() -> service.republish(REVIEWER, Set.of(PROJECT), ids))
                    .isInstanceOf(ResponseStatusException.class);
        }

        verify(tickets, never()).lockForReview(anyCollection());
        assertNothingChanged(published, TicketStatus.PUBLISHED);
    }

    private void assertNothingChanged(Ticket ticket, TicketStatus status) {
        assertThat(ticket.getStatus()).isEqualTo(status);
        verify(decisions, never()).saveAll(any());
        verifyNoInteractions(audit, events);
    }

    @SuppressWarnings("unchecked")
    private List<TicketDecision> savedDecisions() {
        ArgumentCaptor<List<TicketDecision>> saved = ArgumentCaptor.forClass(List.class);
        verify(decisions).saveAll(saved.capture());
        return saved.getValue();
    }

    private final List<Ticket> store = new ArrayList<>();

    /** A ticket the lock query returns, as the real query does: only the ids that exist. */
    private Ticket stored(long id, TicketStatus status, UUID project) {
        Ticket ticket = Ticket.imported(
                "KAN-" + id,
                project,
                TicketSource.SOLUTION_FORM,
                null,
                "Title",
                null,
                null,
                null,
                new Ticket.Solution("s", "r", "steps", null, "scope"));
        ReflectionTestUtils.setField(ticket, "id", new UUID(0, id));
        ReflectionTestUtils.setField(ticket, "status", status);
        ReflectionTestUtils.setField(ticket, "reviewDueDate", DUE);
        store.add(ticket);
        when(tickets.lockForReview(anyCollection())).thenAnswer(call -> {
            var ids = call.<java.util.Collection<UUID>>getArgument(0);
            return store.stream().filter(t -> ids.contains(t.getId())).toList();
        });
        return ticket;
    }
}
