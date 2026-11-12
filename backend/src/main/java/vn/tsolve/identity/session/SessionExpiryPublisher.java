package vn.tsolve.identity.session;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
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
        try {
            for (String member : store.due(now)) {
                // parsed before the claim, so a bad member is never claimed and never loses its event
                SessionEnded ended = parse(member);
                if (ended == null) {
                    // no member text, hash or id in the log
                    log.error("Session expiry sweep dropped one malformed index member");
                    store.drop(member);
                    continue;
                }
                boolean claimed;
                try {
                    claimed = store.claim(member, now);
                } catch (DataAccessException e) {
                    throw e;
                } catch (RuntimeException e) {
                    log.warn(
                            "Session expiry sweep failed for one session ({})",
                            e.getClass().getName());
                    continue;
                }
                if (claimed) {
                    publish(ended);
                }
            }
        } catch (DataAccessException e) {
            // the store is failing: stop this tick, the next one retries; class name only
            log.error(
                    "Session expiry sweep stopped on a store failure ({})",
                    e.getClass().getName());
        }
    }

    /** A listener failure of any type never breaks the loop; the log holds the class name only. */
    private void publish(SessionEnded ended) {
        try {
            events.publishEvent(ended);
        } catch (RuntimeException e) {
            log.warn(
                    "Session expiry sweep: a SessionEnded listener failed ({})",
                    e.getClass().getName());
        }
    }

    /** The event of an index member {@code <hash>|<workspaceId>|<accountId>}, or null if it cannot be parsed. */
    private static SessionEnded parse(String member) {
        String[] parts = member.split("\\|", -1);
        if (parts.length != 3 || parts[0].isEmpty()) {
            return null;
        }
        try {
            return new SessionEnded(
                    UUID.fromString(parts[2]), UUID.fromString(parts[1]), parts[0], SessionEnded.Reason.EXPIRED);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
