package vn.tsolve.ticket.repository;

import org.springframework.data.repository.Repository;
import vn.tsolve.ticket.entity.ReviewAuditEvent;

public interface ReviewAuditRepository extends Repository<ReviewAuditEvent, Long> {
    ReviewAuditEvent save(ReviewAuditEvent event);
}
