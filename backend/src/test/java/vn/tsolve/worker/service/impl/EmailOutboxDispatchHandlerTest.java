package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.tsolve.worker.client.SmtpEmailSender;
import vn.tsolve.worker.config.EmailOutboxProperties;
import vn.tsolve.worker.dto.EmailOutboxDispatchRequest;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.repository.EmailOutboxRepository.Row;
import vn.tsolve.worker.service.WorkerTenantService;

@ExtendWith(OutputCaptureExtension.class)
class EmailOutboxDispatchHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
    private final MutableClock clock = new MutableClock();
    private final EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
    private final WorkerTenantService tenants = mock(WorkerTenantService.class);
    private final SmtpEmailSender sender = mock(SmtpEmailSender.class);
    private final ObjectProvider<SmtpEmailSender> senders = mock(ObjectProvider.class);
    private final EmailOutboxProperties properties = new EmailOutboxProperties(
            Duration.ofSeconds(30),
            List.of(Duration.ofMinutes(5), Duration.ofMinutes(15)),
            Duration.ofMinutes(5),
            20,
            Duration.ofDays(30));
    private final EmailOutboxDispatchHandler handler =
            new EmailOutboxDispatchHandler(repository, tenants, senders, properties, clock);

    @BeforeEach
    void setup() {
        when(senders.getIfAvailable()).thenReturn(sender);
        when(tenants.isWorkspaceActive(any())).thenReturn(true);
    }

    private Row row(int attempts, Instant created, Instant claimed) {
        return new Row(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new OutboundEmail("private@example.test", "private subject", "private body", null),
                attempts,
                created,
                claimed);
    }

    private void due(Row... rows) {
        doAnswer(call -> {
                    call.<Consumer<List<Row>>>getArgument(2).accept(List.of(rows));
                    return null;
                })
                .when(repository)
                .withDueRows(any(), anyInt(), any());
    }

    private void run() {
        handler.run(new EmailOutboxDispatchRequest());
    }

    @Test
    @DisplayName("AC-004.4 AC-004.5 AC-004.6 claims a bounded ordered batch, sends stored data and records send time")
    void sendsStoredMessages() {
        Row first = row(0, NOW.minusSeconds(2), null), second = row(0, NOW, null);
        due(first, second);
        doAnswer(call -> {
                    clock.now = NOW.plusSeconds(2);
                    return null;
                })
                .when(sender)
                .send(first.email());
        run();
        verify(repository).withDueRows(eq(NOW), eq(20), any());
        var order = inOrder(repository, sender);
        order.verify(repository).claim(first, NOW, NOW.plusSeconds(300));
        order.verify(repository).claim(second, NOW, NOW.plusSeconds(300));
        order.verify(sender).send(first.email());
        order.verify(repository).finish(first.claimed(NOW), "SENT", NOW.plusSeconds(2));
        order.verify(sender).send(second.email());
        verify(tenants, never()).runIn(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"0,5", "1,15", "2,0"})
    @DisplayName("AC-004.7 failures one and two back off; failure three finishes")
    void retriesFailures(int previous, int minutes) {
        Row row = row(previous, NOW, null);
        due(row);
        doThrow(new SmtpEmailSender.DeliveryException()).when(sender).send(any());
        run();
        if (minutes == 0) verify(repository).finish(row.claimed(NOW), "FAILED", NOW);
        else verify(repository).reschedule(row.claimed(NOW), NOW.plusSeconds(minutes * 60));
    }

    @Test
    @DisplayName("AC-004.7 unexpected send failure only retries its own row (BUG-212)")
    void continuesAfterUnexpectedSendFailure_BUG_212() {
        Row first = row(0, NOW.minusSeconds(2), null);
        Row second = new Row(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new OutboundEmail("second@example.test", "second subject", "second body", null),
                0,
                NOW,
                null);
        due(first, second);
        doThrow(new IllegalStateException("private@example.test private subject private body"))
                .when(sender)
                .send(first.email());

        run();

        verify(repository).reschedule(first.claimed(NOW), NOW.plusSeconds(300));
        verify(sender).send(second.email());
        verify(repository).finish(second.claimed(NOW), "SENT", NOW);
        verify(repository, never()).finish(eq(first.claimed(NOW)), any(), any());
    }

    @Test
    @DisplayName("AC-004.7 failure time determines the hour boundary")
    void failsAfterHour() {
        Row row = row(0, NOW.minusSeconds(3599), null);
        due(row);
        doAnswer(call -> {
                    clock.now = NOW.plusSeconds(2);
                    throw new SmtpEmailSender.DeliveryException();
                })
                .when(sender)
                .send(any());
        run();
        verify(repository).finish(row.claimed(NOW), "FAILED", NOW.plusSeconds(2));
    }

    @Test
    @DisplayName("AC-004.7 scheduled retries and first attempts past the hour are still sent")
    void attemptsOldUnclaimedRows() {
        Row first = row(0, NOW.minusSeconds(4000), null), retry = row(1, NOW.minusSeconds(4000), null);
        due(first, retry);
        run();
        verify(repository).finish(first.claimed(NOW), "SENT", NOW);
        verify(repository).finish(retry.claimed(NOW), "SENT", NOW);
    }

    @ParameterizedTest
    @CsvSource({"1,60,false", "2,60,false", "3,60,true", "1,3601,true"})
    @DisplayName("AC-004.7 expired leases retry immediately within limits or fail")
    void expiresLease(int attempts, int age, boolean failed) {
        Row row = row(attempts, NOW.minusSeconds(age), NOW.minusSeconds(300));
        due(row);
        run();
        if (failed) {
            verify(repository).finish(row, "FAILED", NOW);
            verifyNoInteractions(sender);
        } else {
            verify(repository).claim(row, NOW, NOW.plusSeconds(300));
            verify(repository).finish(row.claimed(NOW), "SENT", NOW);
        }
    }

    @ParameterizedTest
    @CsvSource({"suspended,60", "unknown,60", "throws,60", "suspended,3601", "unknown,3601", "throws,3601"})
    @DisplayName("AC-004.9 ineligible or unreadable Workspace is held without an attempt and batch continues")
    void holdsIneligible(String eligibility, int age) {
        Row held = row(0, NOW.minusSeconds(age), null), active = row(0, NOW, null);
        due(held, active);
        if (eligibility.equals("throws"))
            when(tenants.isWorkspaceActive(held.workspaceId()))
                    .thenThrow(new IllegalStateException("private@example.test private subject private body"));
        else when(tenants.isWorkspaceActive(held.workspaceId())).thenReturn(false);
        run();
        if (age > 3600) verify(repository).finish(held, "FAILED", NOW);
        else verify(repository).hold(held, NOW.plusSeconds(30));
        verify(repository, never()).claim(eq(held), any(), any());
        verify(repository).finish(active.claimed(NOW), "SENT", NOW);
    }

    @Test
    @DisplayName("AC-004.11 missing sender fails before any rows change")
    void rejectsMissingSender() {
        when(senders.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(this::run).hasMessage("Email sender unavailable").hasNoCause();
        verifyNoInteractions(repository, tenants);
    }

    @Test
    @DisplayName("AC-004.10 retention uses clock and configured batch limit")
    void deletesOldRows() {
        due();
        run();
        verify(repository).deleteFinished(NOW.minus(Duration.ofDays(30)), 20);
    }

    @Test
    @DisplayName("AC-004.11 transport and database errors cannot expose message content")
    void sanitizesErrors_BUG_213(CapturedOutput output) {
        Row row = row(0, NOW, null);
        due(row);
        doThrow(new IllegalStateException("private@example.test private subject private body"))
                .when(repository)
                .claim(any(), any(), any());
        assertThatThrownBy(this::run).hasMessage("Email outbox unavailable").hasNoCause();
        assertThat(output.getAll())
                .contains("WARN", "Email outbox dispatch failed (IllegalStateException)")
                .doesNotContain("private@example.test", "private subject", "private body", "\tat ");
        assertThat(output.getAll().lines().filter(line -> line.contains("Email outbox dispatch failed")))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-004.7 AC-004.11 delivery failures omit all message fields from output")
    void hidesDeliveryFailure(CapturedOutput output) {
        Row row = row(0, NOW, null);
        due(row);
        doThrow(new SmtpEmailSender.DeliveryException()).when(sender).send(any());
        run();
        assertThat(output.getAll())
                .doesNotContain(
                        row.email().recipient(),
                        row.email().subject(),
                        row.email().bodyText());
        verify(repository).reschedule(row.claimed(NOW), NOW.plusSeconds(300));
    }

    static class MutableClock extends Clock {
        Instant now = NOW;

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }
}
