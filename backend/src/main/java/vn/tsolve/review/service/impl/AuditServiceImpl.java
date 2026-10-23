package vn.tsolve.review.service.impl;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.review.entity.ReviewAuditEvent;
import vn.tsolve.review.repository.ReviewAuditRepository;
import vn.tsolve.review.service.AuditService;

@Service
public class AuditServiceImpl implements AuditService {
    private final ReviewAuditRepository repository;

    public AuditServiceImpl(ReviewAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String actor, String action, String resource, Instant at) {
        repository.save(new ReviewAuditEvent(actor, action, resource, at));
    }
}
