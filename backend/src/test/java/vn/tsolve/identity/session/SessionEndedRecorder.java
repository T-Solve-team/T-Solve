package vn.tsolve.identity.session;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Test-only listener that records every {@link SessionEnded} and whether {@code tsolve:session:<sessionId>} still
 * existed when the event arrived (which proves "published after the session is gone"). A test adds it with
 * {@code @Import(SessionEndedRecorder.class)} and calls {@link #reset()} before each test.
 */
@TestComponent
public class SessionEndedRecorder {

    /** One received event and the state of its Redis key at that moment. */
    public record Received(SessionEnded event, boolean keyExistedAtPublish) {}

    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();

    private final StringRedisTemplate redis;

    public SessionEndedRecorder(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @EventListener
    void on(SessionEnded event) {
        boolean exists = Boolean.TRUE.equals(redis.hasKey(SessionTestFixtures.KEY_PREFIX + event.sessionId()));
        RECEIVED.add(new Received(event, exists));
    }

    public static List<Received> all() {
        return List.copyOf(RECEIVED);
    }

    public static List<Received> ofReason(SessionEnded.Reason reason) {
        return RECEIVED.stream().filter(r -> r.event().reason() == reason).toList();
    }

    public static void reset() {
        RECEIVED.clear();
    }
}
