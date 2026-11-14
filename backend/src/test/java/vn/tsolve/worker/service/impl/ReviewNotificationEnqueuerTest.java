package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.event.ReviewFeedbackDecidedEvent;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.client.ReviewEmail;
import vn.tsolve.worker.client.ReviewEmail.Decision;
import vn.tsolve.worker.client.ReviewEmailRenderer;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.EmailOutboxUnavailableException;
import vn.tsolve.worker.service.EmailOutboxService;
import vn.tsolve.worker.service.WorkerTenantService;

@ExtendWith(OutputCaptureExtension.class)
class ReviewNotificationEnqueuerTest {

    private static final UUID DECISION = UUID.randomUUID();
    private static final String WRITER = "writer@example.test";
    private static final String FEEDBACK = "private-review-feedback";

    private final ReviewNotificationService notifications = mock(ReviewNotificationService.class);
    private final EmailOutboxService outbox = mock(EmailOutboxService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<EmailOutboxService> outboxes = mock(ObjectProvider.class);

    private final WorkerTenantService tenants = mock(WorkerTenantService.class);
    private final WorkspaceSiteService sites = mock(WorkspaceSiteService.class);
    private final ReviewNotificationEnqueuer enqueuer =
            new ReviewNotificationEnqueuer(outboxes, tenantProvider(), notifications, sites);

    @SuppressWarnings("unchecked")
    private ObjectProvider<WorkerTenantService> tenantProvider() {
        ObjectProvider<WorkerTenantService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tenants);
        return provider;
    }

    private ReviewNotification notification(ReviewNotification.Kind kind) {
        return new ReviewNotification(
                kind,
                WRITER,
                "ITS-1",
                FEEDBACK,
                new UUID(0, 1),
                "Title",
                "ITS",
                "Reviewer",
                "Writer",
                Instant.parse("2026-11-14T18:42:00Z"));
    }

    @ParameterizedTest
    @EnumSource(ReviewNotification.Kind.class)
    void passesRenderedContentAndTicketLink_AC_010_1_AC_010_2_AC_010_8(ReviewNotification.Kind kind) {
        UUID workspace = UUID.randomUUID();
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(workspace));
        when(sites.siteHost(workspace)).thenReturn(Optional.of("acme.example.test"));
        when(outboxes.getIfAvailable()).thenReturn(outbox);
        when(notifications.findNotification(DECISION)).thenReturn(Optional.of(notification(kind)));
        enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(DECISION)));
        var notification = notification(kind);
        var email = new ReviewEmail(
                kind == ReviewNotification.Kind.REJECTED ? Decision.REJECT : Decision.REQUEST_CHANGES,
                notification.ticketKey(),
                notification.ticketTitle(),
                notification.projectName(),
                notification.reviewerName(),
                notification.writerName(),
                notification.decidedAt(),
                notification.feedback(),
                "https://acme.example.test/tickets/00000000-0000-0000-0000-000000000001",
                "acme.example.test");
        var renderer = new ReviewEmailRenderer();
        verify(outbox)
                .queue(new OutboundEmail(WRITER, renderer.subject(email), renderer.plain(email), renderer.html(email)));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = "   ")
    void omitsTicketLinkWhenSiteUnavailable_AC_010_8(String host) {
        UUID workspace = UUID.randomUUID();
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(workspace));
        when(sites.siteHost(workspace)).thenReturn(Optional.ofNullable(host));
        when(outboxes.getIfAvailable()).thenReturn(outbox);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.CHANGES_REQUESTED)));
        enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(DECISION)));
        var outbound = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(outbox).queue(outbound.capture());
        assertThat(outbound.getValue().recipient()).isEqualTo(WRITER);
        assertThat(outbound.getValue().subject()).isEqualTo("[ITS-1] Changes requested for your ticket");
        assertThat(outbound.getValue().bodyText()).doesNotContain("https://", "in the");
        assertThat(outbound.getValue().bodyHtml()).doesNotContain("<a ", "https://", "in the");
    }

    @Test
    void skipsAbsentOutbox_AC_010_3() {
        assertThatCode(() -> enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(DECISION))))
                .doesNotThrowAnyException();
        verifyNoInteractions(notifications, tenants, outbox, sites);
    }

    @Test
    void skipsApproveMissingDecisionAndNoWriterButQueuesOthers_AC_010_5() {
        when(outboxes.getIfAvailable()).thenReturn(outbox);
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(UUID.randomUUID()));
        UUID approved = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID noWriter = UUID.randomUUID();
        when(notifications.findNotification(approved)).thenReturn(Optional.empty());
        when(notifications.findNotification(missing)).thenReturn(Optional.empty());
        when(notifications.findNotification(noWriter)).thenReturn(Optional.empty());
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.REJECTED)));
        enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(approved, missing, noWriter, DECISION)));
        verify(outbox).queue(any(OutboundEmail.class));
    }

    @Test
    void continuesAfterQueueOrLookupFailureAndLogsNoPrivateData_AC_010_3_AC_010_5_AC_010_6(CapturedOutput output) {
        when(outboxes.getIfAvailable()).thenReturn(outbox);
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(UUID.randomUUID()));
        UUID lookupFailure = UUID.randomUUID();
        UUID queueFailure = UUID.randomUUID();
        String privateMessage = WRITER + " " + FEEDBACK + " private-subject private-body";
        when(notifications.findNotification(lookupFailure)).thenThrow(new IllegalStateException(privateMessage));
        when(notifications.findNotification(queueFailure))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.REJECTED)));
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.CHANGES_REQUESTED)));
        when(outbox.queue(any()))
                .thenThrow(new EmailOutboxUnavailableException())
                .thenReturn(UUID.randomUUID());
        assertThatCode(() -> enqueuer.onDecided(
                        new ReviewFeedbackDecidedEvent(List.of(lookupFailure, queueFailure, DECISION))))
                .doesNotThrowAnyException();
        verify(outbox, times(2)).queue(any());
        assertThat(output.getAll())
                .contains("IllegalStateException", "EmailOutboxUnavailableException")
                .doesNotContain(WRITER, FEEDBACK, "private-subject", "private-body");
    }

    @Test
    void queuesSubjectWithoutLineBreaks_AC_010_9() {
        when(outboxes.getIfAvailable()).thenReturn(outbox);
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(UUID.randomUUID()));
        var n = notification(ReviewNotification.Kind.REJECTED);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(new ReviewNotification(
                        n.kind(),
                        n.recipient(),
                        "ITS-1\r\nInjected",
                        n.feedback(),
                        n.ticketId(),
                        n.ticketTitle(),
                        n.projectName(),
                        n.reviewerName(),
                        n.writerName(),
                        n.decidedAt())));
        enqueuer.onDecided(new ReviewFeedbackDecidedEvent(List.of(DECISION)));
        var outbound = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(outbox).queue(outbound.capture());
        assertThat(outbound.getValue().subject())
                .isEqualTo("[ITS-1  Injected] Your ticket was rejected")
                .doesNotContain("\r", "\n");
    }
}
