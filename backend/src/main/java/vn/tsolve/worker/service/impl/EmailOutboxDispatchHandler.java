package vn.tsolve.worker.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.worker.client.SmtpEmailSender;
import vn.tsolve.worker.config.EmailOutboxProperties;
import vn.tsolve.worker.dto.EmailOutboxDispatchRequest;
import vn.tsolve.worker.exception.EmailOutboxUnavailableException;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.repository.EmailOutboxRepository.Row;
import vn.tsolve.worker.service.WorkerTenantService;

@Component
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "true")
public class EmailOutboxDispatchHandler implements JobRequestHandler<EmailOutboxDispatchRequest> {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatchHandler.class);
    private final EmailOutboxRepository repository;
    private final WorkerTenantService tenants;
    private final ObjectProvider<SmtpEmailSender> senders;
    private final EmailOutboxProperties properties;
    private final Clock clock;

    public EmailOutboxDispatchHandler(
            EmailOutboxRepository repository,
            WorkerTenantService tenants,
            ObjectProvider<SmtpEmailSender> senders,
            EmailOutboxProperties properties,
            @Qualifier("workerClock") Clock clock) {
        this.repository = repository;
        this.tenants = tenants;
        this.senders = senders;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Job(retries = 0)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void run(EmailOutboxDispatchRequest request) {
        SmtpEmailSender sender = senders.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Email sender unavailable");
        try {
            dispatch(sender);
        } catch (RuntimeException failure) {
            // SQL diagnostics and collaborator errors can contain the stored message.
            log.warn("Email outbox dispatch failed ({})", failure.getClass().getSimpleName());
            throw new EmailOutboxUnavailableException();
        }
    }

    private void dispatch(SmtpEmailSender sender) {
        Instant now = clock.instant();
        var claimed = new ArrayList<Row>();
        var activeWorkspaces = new HashMap<UUID, Boolean>();
        repository.withDueRows(now, properties.batchSize(), 10, rows -> {
            for (Row row : rows) {
                if (claimed.size() == properties.batchSize()) break;
                if (row.claimedAt() != null && exhausted(row, now)) {
                    repository.finish(row, "FAILED", now);
                } else if (!activeWorkspaces.computeIfAbsent(row.workspaceId(), this::isActive)) {
                    if (pastHour(row, now)) repository.finish(row, "FAILED", now);
                    else repository.hold(row, now.plus(properties.interval()));
                } else {
                    repository.claim(row, now, now.plus(properties.lease()));
                    claimed.add(row.claimed(now));
                }
            }
            return claimed.size() < properties.batchSize();
        });
        // withDueRows has committed; no database transaction spans SMTP.
        for (Row row : claimed) {
            try {
                try {
                    sender.send(row.email());
                } catch (RuntimeException ignored) {
                    Instant failedAt = clock.instant();
                    if (exhausted(row, failedAt)) repository.finish(row, "FAILED", failedAt);
                    else
                        repository.reschedule(
                                row, failedAt.plus(properties.backoff().get(row.attemptCount() - 1)));
                    continue;
                }
                repository.finish(row, "SENT", clock.instant());
            } catch (RuntimeException failure) {
                log.warn(
                        "Email outbox outcome write failed ({})",
                        failure.getClass().getSimpleName());
            }
        }
        repository.deleteFinished(clock.instant().minus(properties.retention()), properties.batchSize());
    }

    private boolean isActive(UUID workspaceId) {
        try {
            return tenants.isWorkspaceActive(workspaceId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean exhausted(Row row, Instant now) {
        return row.attemptCount() >= 3 || pastHour(row, now);
    }

    private boolean pastHour(Row row, Instant now) {
        return now.isAfter(row.createdAt().plus(Duration.ofMinutes(60)));
    }
}
