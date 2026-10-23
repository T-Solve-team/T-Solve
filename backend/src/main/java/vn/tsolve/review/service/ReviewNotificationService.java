package vn.tsolve.review.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.tsolve.review.dto.PendingReviewNotification;

/** Internal mail integration only: send outside this module's transactions; retry under GBR-27. */
public interface ReviewNotificationService {
    Page<PendingReviewNotification> pending(Pageable pageable);

    void acknowledgeDelivery(long notificationId);
}
