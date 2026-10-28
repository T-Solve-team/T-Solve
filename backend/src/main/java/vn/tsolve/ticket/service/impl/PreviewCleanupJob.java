package vn.tsolve.ticket.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.tsolve.ticket.repository.ImportPreviewRepository;

/** Deletes Import Previews nobody confirmed within 24 hours (spec STORY-006, design D9). */
@Component
public class PreviewCleanupJob {

    static final Duration MAX_AGE = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(PreviewCleanupJob.class);

    private final ImportPreviewRepository previews;
    private final Clock clock;

    public PreviewCleanupJob(ImportPreviewRepository previews, Clock clock) {
        this.previews = previews;
        this.clock = clock;
    }

    /** Every day at 01:00 Vietnam time. Each preview is deleted in its own transaction, so one failure stops nothing. */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Ho_Chi_Minh")
    public int deleteExpiredPreviews() {
        Instant cutoff = Instant.now(clock).minus(MAX_AGE);
        int deleted = 0;
        for (Long id : previews.findIdsCreatedBefore(cutoff)) {
            try {
                previews.deleteById(id);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("Could not delete expired import preview {}", id, e);
            }
        }
        return deleted;
    }
}
