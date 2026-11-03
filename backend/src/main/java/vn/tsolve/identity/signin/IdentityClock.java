package vn.tsolve.identity.signin;

import java.time.Clock;
import java.time.Instant;

/** The identity module's own clock, so tests can move time without a shared {@link Clock} bean. */
public final class IdentityClock {

    private final Clock clock;

    public IdentityClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }
}
