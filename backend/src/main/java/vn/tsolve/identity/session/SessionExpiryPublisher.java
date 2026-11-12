package vn.tsolve.identity.session;

import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.signin.IdentityClock;

/**
 * Every minute, ends the Workspace sessions whose deadline has passed and publishes one {@link SessionEnded}
 * (EXPIRED) for each (AC-002.3, AC-002.4). Only the instance whose claim succeeds publishes, and only after the
 * session is gone.
 */
@Component
public class SessionExpiryPublisher {

    private final WebSessionStore store;
    private final IdentityClock clock;
    private final ApplicationEventPublisher events;

    public SessionExpiryPublisher(WebSessionStore store, IdentityClock clock, ApplicationEventPublisher events) {
        this.store = store;
        this.clock = clock;
        this.events = events;
    }

    @Scheduled(cron = "${TSOLVE_SESSION_SWEEP_CRON:0 * * * * *}")
    public void sweep() {
        long now = clock.now().toEpochMilli();
        for (String member : store.due(now)) {
            if (store.claim(member, now)) {
                String[] parts = member.split("\\|");
                events.publishEvent(new SessionEnded(
                        UUID.fromString(parts[2]), UUID.fromString(parts[1]), parts[0], SessionEnded.Reason.EXPIRED));
            }
        }
    }
}
