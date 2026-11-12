package vn.tsolve.identity.session;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SessionExpiryPublisher.class);

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
            try {
                String[] parts = member.split("\\|");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("malformed member");
                }
                // parsed before the claim, so a bad member is never claimed and never loses its event
                SessionEnded ended = new SessionEnded(
                        UUID.fromString(parts[2]), UUID.fromString(parts[1]), parts[0], SessionEnded.Reason.EXPIRED);
                if (store.claim(member, now)) {
                    events.publishEvent(ended);
                }
            } catch (IllegalArgumentException e) {
                store.drop(member);
            } catch (RuntimeException e) {
                log.warn(
                        "Session expiry sweep failed for one session: {}",
                        e.getClass().getSimpleName());
            }
        }
    }
}
