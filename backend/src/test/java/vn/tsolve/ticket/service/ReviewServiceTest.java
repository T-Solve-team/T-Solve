package vn.tsolve.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.ticket.dto.ReviewAction;
import vn.tsolve.ticket.dto.ReviewActor;
import vn.tsolve.ticket.dto.ReviewRegistration;
import vn.tsolve.ticket.dto.ReviewSource;
import vn.tsolve.ticket.dto.ReviewStatus;
import vn.tsolve.ticket.dto.SolutionSections;
import vn.tsolve.ticket.entity.ReviewDecision;
import vn.tsolve.ticket.entity.ReviewNotification;
import vn.tsolve.ticket.entity.ReviewTicket;
import vn.tsolve.ticket.exception.ReviewException;
import vn.tsolve.ticket.mapper.ReviewMapper;
import vn.tsolve.ticket.repository.ReviewDecisionRepository;
import vn.tsolve.ticket.repository.ReviewNotificationRepository;
import vn.tsolve.ticket.repository.ReviewTicketRepository;
import vn.tsolve.ticket.service.impl.ReviewServiceImpl;

class ReviewServiceTest {
    private final ObjectProvider<ReviewAccessService> access = mock();
    private final ReviewAccessService adapter = mock();
    private final ReviewTicketRepository tickets = mock();
    private final ReviewDecisionRepository decisions = mock();
    private final ReviewNotificationRepository notifications = mock();
    private final AuditService audit = mock();
    private ReviewService service;

    @BeforeEach
    void setup() {
        service = new ReviewServiceImpl(
                access, tickets, Mappers.getMapper(ReviewMapper.class), decisions, notifications, audit);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "AC-006.2 denies access without an auth adapter; Mockito executes on JDK 21 (BUG-004)")
    void deniesWhenIdentityIntegrationIsAbsent_AC_006_2() {
        assertThatThrownBy(() -> service.queue(null, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(ReviewException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG08"));
        verify(tickets, never()).findByProjectIdInAndStatusAndSource(any(), any(), any(), any());
    }

    @Test
    void queueIsConstrainedToManagedProjects_AC_001_1_AC_001_2_AC_006_1() {
        when(access.getIfAvailable()).thenReturn(adapter);
        when(adapter.currentActor()).thenReturn(new ReviewActor("pm", "Manager", Map.of(10L, "IT"), Set.of(10L)));
        when(tickets.findByProjectIdInAndStatusAndSource(any(), any(), any(), any()))
                .thenReturn(Page.empty());
        service.queue(null, PageRequest.of(0, 20));
        verify(tickets)
                .findByProjectIdInAndStatusAndSource(
                        eq(Set.of(10L)), eq(ReviewStatus.PENDING_REVIEW), eq(ReviewSource.SOLUTION_FORM), any());
    }

    @Test
    void rejectsForeignProjectFilter_AC_001_5() {
        manager();
        assertThatThrownBy(() -> service.queue(99L, PageRequest.of(0, 20))).isInstanceOf(ReviewException.class);
        verifyNoInteractions(tickets);
    }

    @Test
    void allIdsAreFilteredOnServerAcrossPages_AC_001_3() {
        manager();
        when(tickets.findQueueIds(Set.of(10L), ReviewStatus.PENDING_REVIEW, ReviewSource.SOLUTION_FORM))
                .thenReturn(List.of(1L, 2L, 3L));
        assertThat(service.queueTicketIds(10L)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void limitsSortingToDocumentedColumns_AC_001_2() {
        manager();
        assertThatThrownBy(() -> service.queue(null, PageRequest.of(0, 20, Sort.by("writerId"))))
                .isInstanceOf(ReviewException.class);
        verifyNoInteractions(tickets);
    }

    @Test
    void preservesPagingAndAddsStableDueDateSort_AC_001_1_AC_001_2() {
        manager();
        when(tickets.findByProjectIdInAndStatusAndSource(any(), any(), any(), any()))
                .thenReturn(Page.empty());
        service.queue(null, PageRequest.of(2, 20));
        ArgumentCaptor<org.springframework.data.domain.Pageable> paging =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(tickets).findByProjectIdInAndStatusAndSource(any(), any(), any(), paging.capture());
        assertThat(paging.getValue().getPageNumber()).isEqualTo(2);
        assertThat(paging.getValue().getPageSize()).isEqualTo(20);
        assertThat(paging.getValue().getSort().toList())
                .containsExactly(Sort.Order.asc("reviewDueDate"), Sort.Order.asc("id"));
    }

    @Test
    void rejectsDuplicateEmptyAndInvalidSelections_AC_005_5() {
        manager();
        for (List<Long> ids : List.of(List.<Long>of(), List.of(1L, 1L), List.of(0L), Arrays.asList(1L, null))) {
            assertThatThrownBy(() -> service.decide(ids, ReviewAction.APPROVE, null))
                    .isInstanceOf(ReviewException.class);
        }
        verifyNoInteractions(tickets, decisions, notifications, audit);
    }

    @Test
    void refusesMoreThanOneChangeTarget_AC_004_4_BUG_001() {
        manager();
        assertThatThrownBy(() -> service.decide(List.of(1L, 2L), ReviewAction.REQUEST_CHANGES, "Add steps"))
                .isInstanceOf(ReviewException.class);
        verifyNoInteractions(tickets, decisions, notifications, audit);
    }

    @Test
    void validatesWholeBatchBeforeMutatingAnyTicket_AC_005_1() {
        manager();
        ReviewTicket first = ticket(1L, 10L);
        when(tickets.lockById(1L)).thenReturn(Optional.of(first));
        when(tickets.lockById(2L)).thenReturn(Optional.of(ticket(2L, 99L)));
        assertThatThrownBy(() -> service.decide(List.of(1L, 2L), ReviewAction.APPROVE, null))
                .isInstanceOf(ReviewException.class);
        assertThat(first.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        verifyNoInteractions(decisions, notifications, audit);
    }

    @Test
    void missingTicketLeavesEarlierTicketUnchanged_AC_005_1() {
        manager();
        ReviewTicket first = ticket(1L, 10L);
        when(tickets.lockById(1L)).thenReturn(Optional.of(first));
        when(tickets.lockById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decide(List.of(1L, 2L), ReviewAction.REJECT, "Incorrect"))
                .isInstanceOfSatisfying(
                        ReviewException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(404));
        assertThat(first.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        verifyNoInteractions(decisions, notifications, audit);
    }

    @Test
    void writesDecisionAuditAndPendingNotification_AC_003_2_AC_005_2_AC_006_3() {
        manager();
        when(tickets.lockById(1L)).thenReturn(Optional.of(ticket(1L, 10L)));
        when(decisions.save(any())).thenAnswer(call -> {
            ReviewDecision decision = call.getArgument(0);
            ReflectionTestUtils.setField(decision, "id", 50L);
            return decision;
        });
        assertThat(service.decide(List.of(1L), ReviewAction.REJECT, "Incorrect").count())
                .isEqualTo(1);
        ArgumentCaptor<ReviewNotification> pending = ArgumentCaptor.forClass(ReviewNotification.class);
        verify(notifications).save(pending.capture());
        assertThat(pending.getValue().getDecisionId()).isEqualTo(50L);
        assertThat(pending.getValue().getRecipientId()).isEqualTo("writer");
        assertThat(pending.getValue().getComment()).isEqualTo("Incorrect");
        verify(audit).record(eq("pm"), eq("REJECT"), eq("ticket:1"), any());
    }

    @Test
    void publishedLookupRequiresReadableProjectAndPublishedState_AC_005_6() {
        manager();
        ReviewTicket pending = ticket(1L, 10L);
        when(tickets.findById(1L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.getPublished(1L)).isInstanceOf(ReviewException.class);
        pending.decide(ReviewAction.APPROVE, null, Instant.now());
        assertThat(service.getPublished(1L).status()).isEqualTo(ReviewStatus.PUBLISHED);
        when(adapter.currentActor()).thenReturn(new ReviewActor("other", "Other", Map.of(), Set.of(99L)));
        assertThatThrownBy(() -> service.getPublished(1L)).isInstanceOf(ReviewException.class);
    }

    @Test
    void nullIdentityIsUnauthorized_AC_001_5_AC_006_2() {
        when(access.getIfAvailable()).thenReturn(adapter);
        assertThatThrownBy(() -> service.queueTicketIds(null))
                .isInstanceOfSatisfying(
                        ReviewException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(401));
        verifyNoInteractions(tickets);
    }

    private void manager() {
        when(access.getIfAvailable()).thenReturn(adapter);
        when(adapter.currentActor()).thenReturn(new ReviewActor("pm", "Manager", Map.of(10L, "IT"), Set.of(10L)));
    }

    private ReviewTicket ticket(long id, long project) {
        Instant now = Instant.parse("2026-10-20T09:00:00Z");
        ReviewTicket ticket = new ReviewTicket(new ReviewRegistration(
                "IT-" + id,
                "VPN",
                project,
                "IT",
                ReviewSource.SOLUTION_FORM,
                "writer",
                "Writer",
                new SolutionSections("Fails", "Cert", "Renew", null, "VPN"),
                List.of(),
                now,
                now.plusSeconds(86400)));
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }
}
