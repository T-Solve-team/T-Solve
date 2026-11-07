package vn.tsolve.search.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.tsolve.search.embedding.service.TicketEmbeddingService;
import vn.tsolve.ticket.event.TicketsPublishedEvent;

@ExtendWith(OutputCaptureExtension.class)
class TicketsPublishedListenerTest {
    @Test
    void neverThrowsIntoTheImportCommit_AC_005_5(CapturedOutput output) throws Exception {
        TicketEmbeddingService service = mock(TicketEmbeddingService.class);
        TaskExecutor rejected = task -> {
            throw new RejectedExecutionException("queue full");
        };
        TicketsPublishedEvent event = new TicketsPublishedEvent(List.of(new UUID(0, 1)));
        assertThatCode(() -> new TicketsPublishedListener(service, rejected).on(event))
                .doesNotThrowAnyException();

        new TicketsPublishedListener(service, Runnable::run).on(event);
        verify(service).embed(event.ticketIds());
        doThrow(new IllegalStateException("sensitive text")).when(service).embed(event.ticketIds());
        assertThatCode(() -> new TicketsPublishedListener(service, Runnable::run).on(event))
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
}
