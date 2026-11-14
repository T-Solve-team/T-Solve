package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.client.ReviewEmail;
import vn.tsolve.worker.client.ReviewEmail.Decision;
import vn.tsolve.worker.client.ReviewEmailRenderer;
import vn.tsolve.worker.client.ReviewNotificationSender;
import vn.tsolve.worker.service.WorkerTenantService;

@ExtendWith(OutputCaptureExtension.class)
class ReviewNotificationJobHandlerTest {

    private static final UUID DECISION = UUID.randomUUID();
    private static final String WRITER = "writer@example.test";
    private static final String FEEDBACK = "private-review-feedback";

    private final ReviewNotificationService notifications = mock(ReviewNotificationService.class);
    private final ReviewNotificationSender sender = mock(ReviewNotificationSender.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ReviewNotificationSender> senders = mock(ObjectProvider.class);

    private final WorkerTenantService tenants = mock(WorkerTenantService.class);
    private final WorkspaceSiteService sites = mock(WorkspaceSiteService.class);
    private final ReviewNotificationJobHandler handler =
            new ReviewNotificationJobHandler(notifications, senders, tenants, sites);

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

    @Test
    void passesRenderedContentAndTicketLink_AC_010_2_AC_010_8() {
        UUID workspace = UUID.randomUUID();
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(workspace));
        when(sites.siteHost(workspace)).thenReturn(Optional.of("acme.example.test"));
        when(senders.getIfAvailable()).thenReturn(sender);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.REJECTED)));
        handler.execute(DECISION);
        var notification = notification(ReviewNotification.Kind.REJECTED);
        var email = new ReviewEmail(
                Decision.REJECT,
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
        verify(sender).send(WRITER, renderer.subject(email), renderer.plain(email), renderer.html(email));
    }

    @Test
    void omitsTicketLinkWhenSiteUnavailable_AC_010_8() {
        UUID workspace = UUID.randomUUID();
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(workspace));
        when(sites.siteHost(workspace)).thenReturn(Optional.empty());
        when(senders.getIfAvailable()).thenReturn(sender);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.CHANGES_REQUESTED)));
        handler.execute(DECISION);
        var subject = ArgumentCaptor.forClass(String.class);
        var text = ArgumentCaptor.forClass(String.class);
        var html = ArgumentCaptor.forClass(String.class);
        verify(sender).send(org.mockito.ArgumentMatchers.eq(WRITER), subject.capture(), text.capture(), html.capture());
        assertThat(subject.getValue()).isEqualTo("[ITS-1] Changes requested for your ticket");
        assertThat(text.getValue()).doesNotContain("https://", "in the");
        assertThat(html.getValue()).doesNotContain("<a ", "https://", "in the");
    }

    @Test
    void failsTheJobSoItIsRetriedWhenDeliveryFails_AC_010_4_AC_010_6(CapturedOutput output) {
        when(senders.getIfAvailable()).thenReturn(sender);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.REJECTED)));
        doThrow(mock(ReviewNotificationSender.DeliveryException.class))
                .when(sender)
                .send(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> handler.execute(DECISION)).isInstanceOf(RuntimeException.class);
        assertThat(output.getAll()).doesNotContain(WRITER).doesNotContain(FEEDBACK);
    }

    @Test
    void failsTheJobWhenThisProcessHasNoSmtpSender_AC_010_4() {
        when(senders.getIfAvailable()).thenReturn(null);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(notification(ReviewNotification.Kind.CHANGES_REQUESTED)));

        assertThatThrownBy(() -> handler.execute(DECISION)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usesItsOwnJobType_AC_010_1() {
        assertThat(handler.handlerType()).isEqualTo(ReviewNotificationJobHandler.TYPE);
    }
}
