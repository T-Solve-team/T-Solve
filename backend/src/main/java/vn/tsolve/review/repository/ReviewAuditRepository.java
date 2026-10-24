package vn.tsolve.review.repository;

import org.springframework.data.repository.Repository;
import vn.tsolve.review.entity.ReviewAuditEvent;

public interface ReviewAuditRepository extends Repository<ReviewAuditEvent, Long> {
    ReviewAuditEvent save(ReviewAuditEvent event);
}
