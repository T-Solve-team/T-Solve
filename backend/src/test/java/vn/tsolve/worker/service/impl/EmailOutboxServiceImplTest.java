package vn.tsolve.worker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.EmailOutboxUnavailableException;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;
import vn.tsolve.worker.repository.EmailOutboxRepository;
import vn.tsolve.worker.service.WorkerTenantService;

@ExtendWith(OutputCaptureExtension.class)
class EmailOutboxServiceImplTest {
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
    private final EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
    private final WorkerTenantService tenants = mock(WorkerTenantService.class);
    private final EmailOutboxServiceImpl service =
            new EmailOutboxServiceImpl(repository, tenants, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("AC-004.1 captures the bound Workspace and ready-made message with creation time")
    void queuesMessage() {
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(WORKSPACE));
        UUID id = service.queue(new OutboundEmail("Writer <writer@example.test>", "Subject", "Text", "<p>Text</p>"));
        assertThat(id).isNotNull();
        verify(repository)
                .insert(id, WORKSPACE, new OutboundEmail("writer@example.test", "Subject", "Text", "<p>Text</p>"), NOW);
        verify(tenants, never()).isWorkspaceActive(any());
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    @DisplayName("AC-004.2 rejects invalid input without inserting")
    void rejectsInvalid(OutboundEmail email) {
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(WORKSPACE));
        assertThatThrownBy(() -> service.queue(email))
                .isInstanceOf(InvalidOutboundEmailException.class)
                .hasMessage("Invalid outbound email")
                .hasNoCause();
        verifyNoInteractions(repository);
    }

    static Stream<OutboundEmail> invalidMessages() {
        return Stream.of(
                null,
                new OutboundEmail("a@example.test,b@example.test", "Subject", "Text", null),
                new OutboundEmail("team:a@example.test;", "Subject", "Text", null),
                new OutboundEmail("a@example.test\r\nBcc: b@example.test", "Subject", "Text", null),
                new OutboundEmail("a@example.test", " ", "Text", null),
                new OutboundEmail("a@example.test", "Subject\nBcc", "Text", null),
                new OutboundEmail("a@example.test", "Subject", " ", null),
                new OutboundEmail("a@example.test", "x".repeat(256), "Text", null),
                new OutboundEmail("a".repeat(310) + "@example.test", "Subject", "Text", null));
    }

    @Test
    @DisplayName("AC-004.2 no Workspace is invalid input")
    void rejectsUnboundWorkspace() {
        when(tenants.currentWorkspaceId()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.queue(new OutboundEmail("a@example.test", "Subject", "Text", null)))
                .isInstanceOf(InvalidOutboundEmailException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("AC-004.3 AC-004.11 database failures expose no mail data in errors or logs")
    void sanitizesFailure(CapturedOutput output) {
        when(tenants.currentWorkspaceId()).thenReturn(Optional.of(WORKSPACE));
        doThrow(new DataAccessResourceFailureException("writer@example.test private-subject private-body"))
                .when(repository)
                .insert(any(), any(), any(), any());
        assertThatThrownBy(() -> service.queue(
                        new OutboundEmail("writer@example.test", "private-subject", "private-body", null)))
                .isInstanceOf(EmailOutboxUnavailableException.class)
                .hasMessage("Email outbox unavailable")
                .hasNoCause();
        assertThat(output.getAll()).doesNotContain("writer@example.test", "private-subject", "private-body");
    }
}
