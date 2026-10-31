package vn.tsolve.identity.signin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock the tests move by hand. Starts at {@link #START} (UTC); only {@link Instant}s matter to sign-in. */
public final class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2026-10-29T02:00:00Z");

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock() {
        this(new AtomicReference<>(START), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    public void reset() {
        now.set(START);
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now, newZone);
    }
}
