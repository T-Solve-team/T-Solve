package vn.tsolve.review.service.impl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.review.repository.ReviewExpiryRepository;
import vn.tsolve.review.service.ReviewExpiryService;
import vn.tsolve.worker.service.WorkerTenantService;

@Service
public class ReviewExpiryServiceImpl implements ReviewExpiryService {
    private static final Logger log = LoggerFactory.getLogger(ReviewExpiryServiceImpl.class);
    private final ReviewExpiryRepository repository;
    private final ObjectProvider<WorkerTenantService> tenants;

    public ReviewExpiryServiceImpl(ReviewExpiryRepository repository, ObjectProvider<WorkerTenantService> tenants) {
        this.repository = repository;
        this.tenants = tenants;
    }

    @Override
    @Transactional
    public Map<UUID, Integer> expireOverdue(LocalDate today) {
        Map<UUID, Integer> counts = new HashMap<>();
        repository.expireOverdue(today).forEach(project -> counts.merge(project, 1, Integer::sum));
        if (!counts.isEmpty()) {
            WorkerTenantService tenant = tenants.getIfAvailable();
            String workspace = tenant == null
                    ? "unbound"
                    : tenant.currentWorkspaceId().map(UUID::toString).orElse("unbound");
            Runnable report = () -> counts.forEach((project, count) ->
                    log.info("JOB-02 expired {} tickets in Project {} of Workspace {}", count, project, workspace));
            // Do not report a successful expiry if the surrounding transaction later rolls back.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        report.run();
                    }
                });
            } else {
                report.run();
            }
        }
        return Map.copyOf(counts);
    }
}
