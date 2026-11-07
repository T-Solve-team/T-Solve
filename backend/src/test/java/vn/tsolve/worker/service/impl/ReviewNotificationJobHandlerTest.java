package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.ticket.dto.ReviewNotification;
import vn.tsolve.ticket.service.ReviewNotificationService;
import vn.tsolve.worker.client.ReviewNotificationSender;
import vn.tsolve.worker.client.ReviewNotificationSender.Decision;

@ExtendWith(OutputCaptureExtension.class)
class ReviewNotificationJobHandlerTest {

    private static final UUID DECISION = UUID.randomUUID();
    private static final String WRITER = "writer@example.test";
    private static final String FEEDBACK = "private-review-feedback";

    private final ReviewNotificationService notifications = mock(ReviewNotificationService.class);
    private final ReviewNotificationSender sender = mock(ReviewNotificationSender.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ReviewNotificationSender> senders = mock(ObjectProvider.class);

    private final ReviewNotificationJobHandler handler = new ReviewNotificationJobHandler(notifications, senders);

    @Test
    void failsTheJobSoItIsRetriedWhenDeliveryFails_AC_010_4_AC_010_6(CapturedOutput output) {
        when(senders.getIfAvailable()).thenReturn(sender);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(
                        new ReviewNotification(ReviewNotification.Kind.REJECTED, WRITER, "ITS-1", FEEDBACK)));
        doThrow(mock(ReviewNotificationSender.DeliveryException.class))
                .when(sender)
                .send(any(Decision.class), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> handler.execute(DECISION)).isInstanceOf(RuntimeException.class);
        assertThat(output.getAll()).doesNotContain(WRITER).doesNotContain(FEEDBACK);
    }

    @Test
    void failsTheJobWhenThisProcessHasNoSmtpSender_AC_010_4() {
        when(senders.getIfAvailable()).thenReturn(null);
        when(notifications.findNotification(DECISION))
                .thenReturn(Optional.of(
                        new ReviewNotification(ReviewNotification.Kind.CHANGES_REQUESTED, WRITER, "ITS-1", FEEDBACK)));

        assertThatThrownBy(() -> handler.execute(DECISION)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usesItsOwnJobType_AC_010_1() {
        assertThat(handler.handlerType()).isEqualTo(ReviewNotificationJobHandler.TYPE);
    }
}
