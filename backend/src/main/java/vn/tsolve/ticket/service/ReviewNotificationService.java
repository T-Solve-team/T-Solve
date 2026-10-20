package vn.tsolve.ticket.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.ticket.dto.PendingReviewNotification;

/** Internal mail integration only: send outside this module's transactions; retry under GBR-27. */
public interface ReviewNotificationService {
    Page<PendingReviewNotification> pending(Pageable pageable);

    void acknowledgeDelivery(long notificationId);
}
