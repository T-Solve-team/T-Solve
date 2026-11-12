package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/** Finding (d): the 15-minute bound of AC-002.4 rests on the default of the sweep's cron, so it is read here. */
class SessionExpiryPublisherTest {

    @Test
    @DisplayName("edge 1 (finding d) the sweep's cron default fires at least every 15 minutes")
    void sweepCronDefaultFiresAtLeastEveryFifteenMinutes_edge1() throws Exception {
        String cron = SessionExpiryPublisher.class
                .getMethod("sweep")
                .getAnnotation(Scheduled.class)
                .cron();
        // "${TSOLVE_SESSION_SWEEP_CRON:<default>}"
        assertThat(cron).startsWith("${TSOLVE_SESSION_SWEEP_CRON:").endsWith("}");
        String fallback = cron.substring(cron.indexOf(':') + 1, cron.length() - 1);
        CronExpression expression = CronExpression.parse(fallback);

        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0, 30);
        for (int i = 0; i < 60; i++) {
            LocalDateTime next = expression.next(t);
            assertThat(next).isNotNull();
            assertThat(Duration.between(t, next)).isLessThanOrEqualTo(Duration.ofMinutes(15));
            t = next;
        }
    }
}
