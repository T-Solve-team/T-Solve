package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import vn.tsolve.identity.signin.IdentityClock;

/**
 * Finding (d): the 15-minute bound of AC-002.4 rests on the default of the sweep's cron, so it is read here. Task 27c:
 * the sweep stops on a store failure.
 */
@ExtendWith(OutputCaptureExtension.class)
class SessionExpiryPublisherTest {

    @Test
    @DisplayName("edge 144 a store failure stops the sweep after one try and logs one ERROR line with the class name")
    void storeFailureStopsTheSweepAndLogsOnce_edge144(CapturedOutput output) {
        WebSessionStore store = mock(WebSessionStore.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        IdentityClock clock = new IdentityClock(Clock.fixed(Instant.parse("2026-11-01T09:00:00Z"), ZoneOffset.UTC));
        Set<String> due = new LinkedHashSet<>();
        for (int i = 0; i < 3; i++) {
            due.add("%064d|%s|%s".formatted(i, UUID.randomUUID(), UUID.randomUUID()));
        }
        when(store.due(anyLong())).thenReturn(due);
        when(store.claim(anyString(), anyLong())).thenThrow(new QueryTimeoutException("secret-text"));

        new SessionExpiryPublisher(store, clock, events).sweep();

        verify(store, times(1)).claim(anyString(), anyLong());
        verifyNoInteractions(events);
        List<String> lines = output.getAll()
                .lines()
                .filter(line -> line.contains("SessionExpiryPublisher"))
                .toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("ERROR").contains("QueryTimeoutException");
        assertThat(output.getAll()).doesNotContain("secret-text");
    }

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
