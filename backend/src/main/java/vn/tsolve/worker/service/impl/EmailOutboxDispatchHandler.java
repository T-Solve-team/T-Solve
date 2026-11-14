package vn.tsolve.worker.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean missingSenderReported = new AtomicBoolean();

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
        if (sender == null && missingSenderReported.compareAndSet(false, true)) {
            log.info("Email outbox rows will not be sent: no SMTP sender is configured (set SPRING_MAIL_HOST)");
        }
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
        if (sender == null) {
            // Nothing can be sent, but rows must still expire and be deleted so address and body are not kept forever.
            expireWithoutSender(now);
            repository.deleteFinished(clock.instant().minus(properties.retention()), properties.batchSize());
            return;
        }
        var claimed = new ArrayList<Row>();
        var activeWorkspaces = new HashMap<UUID, Boolean>();
        repository.withDueRows(now, properties.batchSize(), 10, rows -> {
            for (Row row : rows) {
                if (claimed.size() == properties.batchSize()) break;
                if (row.claimedAt() != null && exhausted(row, now)) {
                    fail(row, now);
                } else if (!activeWorkspaces.computeIfAbsent(row.workspaceId(), this::isActive)) {
                    if (pastHour(row, now)) fail(row, now);
                    else repository.hold(row, now.plus(heldDelay()));
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
                    if (exhausted(row, failedAt)) fail(row, failedAt);
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

    /** Rows are ordered by age, so the first unexpired row ends the scan. */
    private void expireWithoutSender(Instant now) {
        repository.withDueRows(now, properties.batchSize(), 10, rows -> {
            boolean allExpired = true;
            for (Row row : rows) {
                if (exhausted(row, now)) {
                    fail(row, now);
                } else {
                    allExpired = false;
                    break;
                }
            }
            return allExpired && rows.size() == properties.batchSize();
        });
    }

    private void fail(Row row, Instant failedAt) {
        // The id and attempt count identify the row without writing the address, subject or body to the log.
        log.warn("Email outbox row {} failed after {} attempts", row.id(), row.attemptCount());
        repository.finish(row, "FAILED", failedAt);
    }

    /**
     * A held row is re-read every run and, ordered by age, would starve newer rows of Active Workspaces once more than
     * a run's reads (batch size x 10) are held (BUG-218), so it waits as long as a first retry.
     */
    private Duration heldDelay() {
        return properties.backoff().get(0);
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
