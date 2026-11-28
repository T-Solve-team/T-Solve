package vn.tsolve.search.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;
import vn.tsolve.ticket.event.TicketsPublishedEvent;

@ExtendWith(OutputCaptureExtension.class)
class TicketsPublishedListenerTest {
    private final TenantContext tenants = new ThreadLocalTenantContext(id -> true);

    @Test
    void neverThrowsIntoTheImportCommit_AC_005_5(CapturedOutput output) throws Exception {
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        TaskExecutor rejected = task -> {
            throw new RejectedExecutionException("queue full");
        };
        TicketsPublishedEvent event = new TicketsPublishedEvent(List.of(new UUID(0, 1)));
        assertThatCode(() -> new TicketsPublishedListener(service, rejected, tenants).on(event))
                .doesNotThrowAnyException();

        new TicketsPublishedListener(service, Runnable::run, tenants).on(event);
        verify(service).embed(event.ticketIds());
        doThrow(new IllegalStateException("sensitive text")).when(service).embed(event.ticketIds());
        assertThatCode(() -> new TicketsPublishedListener(service, Runnable::run, tenants).on(event))
                .doesNotThrowAnyException();
        assertThat(output)
                .contains(
                        "embedding dispatch rejected for tickets [" + new UUID(0, 1)
                                + "] cause=RejectedExecutionException",
                        "embedding failed for tickets [" + new UUID(0, 1) + "] cause=IllegalStateException")
                .doesNotContain("queue full", "sensitive text");
        assertThat(TicketsPublishedListener.class
                        .getDeclaredMethod("on", TicketsPublishedEvent.class)
                        .getAnnotation(TransactionalEventListener.class)
                        .phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void embedsInThePublishersWorkspaceOnTheExecutorThread_BUG_402() {
        UUID workspace = UUID.randomUUID();
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        TicketsPublishedEvent event = new TicketsPublishedEvent(List.of(new UUID(0, 1)));
        List<Optional<UUID>> bound = new ArrayList<>();
        doAnswer(call -> bound.add(tenants.currentWorkspaceId())).when(service).embed(event.ticketIds());
        AtomicReference<Runnable> task = new AtomicReference<>();
        TicketsPublishedListener listener = new TicketsPublishedListener(service, task::set, tenants);

        tenants.runIn(workspace, () -> listener.on(event));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
        task.get().run();

        assertThat(bound).containsExactly(Optional.of(workspace));
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }

    @Test
    void embedsUnboundWhenThePublisherHasNoWorkspace_BUG_402(CapturedOutput output) {
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        TicketsPublishedEvent event = new TicketsPublishedEvent(List.of(new UUID(0, 1)));
        List<Optional<UUID>> bound = new ArrayList<>();
        doAnswer(call -> bound.add(tenants.currentWorkspaceId())).when(service).embed(event.ticketIds());
        TicketsPublishedListener listener = new TicketsPublishedListener(service, Runnable::run, tenants);

        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();

        verify(service).embed(event.ticketIds());
        assertThat(bound).containsExactly(Optional.empty());

        doThrow(new IllegalStateException("sensitive text")).when(service).embed(event.ticketIds());
        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
        assertThat(output)
                .contains("embedding failed for tickets [" + new UUID(0, 1) + "] cause=IllegalStateException")
                .doesNotContain("sensitive text");
        assertThat(tenants.currentWorkspaceId()).isEmpty();
    }
}
