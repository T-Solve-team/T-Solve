package vn.tsolve.ticket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.ticket.entity.ReviewNotification;

public interface ReviewNotificationRepository extends JpaRepository<ReviewNotification, Long> {
    Page<ReviewNotification> findByDeliveredAtIsNull(Pageable pageable);
}
