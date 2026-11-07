package vn.tsolve.search.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.tsolve.ai.client.EmbeddingClient;
import vn.tsolve.ai.exception.AiUnavailableException;
import vn.tsolve.search.embedding.repository.EmbeddingTicketRepository;
import vn.tsolve.search.embedding.repository.TicketEmbeddingRepository;
import vn.tsolve.search.scope.entity.SearchTicket;
import vn.tsolve.ticket.event.TicketsPublishedEvent;

@ExtendWith(OutputCaptureExtension.class)
class TicketEmbeddingServiceImplTest {
    private final EmbeddingTicketRepository tickets = mock(EmbeddingTicketRepository.class);
    private final TicketEmbeddingRepository embeddings = mock(TicketEmbeddingRepository.class);
    private final EmbeddingClient client = mock(EmbeddingClient.class);
    private TicketEmbeddingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketEmbeddingServiceImpl(tickets, embeddings, client, new Transactions(), 2, "bge-m3");
        when(tickets.findByIdInAndStatus(anyList(), eq("PUBLISHED"))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                    .isTrue();
            List<UUID> ids = call.getArgument(0);
            return ids.stream().map(this::ticket).toList();
        });
        when(client.embed(anyList())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            List<String> texts = call.getArgument(0);
            return texts.stream().map(text -> new float[1024]).toList();
        });
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isTrue();
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                            .isFalse();
                    return null;
                })
                .when(embeddings)
                .upsert(any(), any(), any());
    }

    @Test
    void embedsPublishedTicketsInBatches_AC_005_4() {
        service.embed(List.of(id(1), id(2), id(3), id(4), id(5)));
        verify(client, times(2)).embed(List.of("private title", "private title"));
        verify(client).embed(List.of("private title"));
        for (long n = 1; n <= 5; n++) {
            verify(embeddings).upsert(eq(id(n)), any(), eq("bge-m3"));
        }
    }

    @Test
    void logsOnlyIdsWhenOllamaFails_AC_005_6(CapturedOutput output) {
        when(client.embed(anyList()))
                .thenThrow(new AiUnavailableException())
                .thenThrow(new IllegalStateException("private exception text"))
                .thenReturn(List.of(new float[1024]));
        service.embed(List.of(id(1), id(2), id(3), id(4), id(5)));
        verify(embeddings, never()).upsert(eq(id(1)), any(), any());
        verify(embeddings, never()).upsert(eq(id(3)), any(), any());
        verify(embeddings).upsert(eq(id(5)), any(), any());
        assertThat(output)
                .contains(
                        "embedding failed for tickets [" + id(1) + ", " + id(2) + "] cause=AiUnavailableException",
                        "embedding failed for tickets [" + id(3) + ", " + id(4) + "] cause=IllegalStateException")
                .doesNotContain("private title", "private exception text");
    }

    @Test
    void skipsMissingAndUnpublishedIds_AC_005_8() {
        doReturn(List.of(ticket(id(2)))).when(tickets).findByIdInAndStatus(List.of(id(1), id(2)), "PUBLISHED");
        doReturn(List.of()).when(tickets).findByIdInAndStatus(List.of(id(3)), "PUBLISHED");
        service.embed(List.of(id(1), id(2), id(3)));
        verify(client).embed(List.of("private title"));
        verify(embeddings).upsert(eq(id(2)), any(), any());
        verify(embeddings, never()).upsert(eq(id(1)), any(), any());
        verify(embeddings, never()).upsert(eq(id(3)), any(), any());
    }

    @Test
    void sendsOneBatchAtATime_AC_005_7() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        when(client.embed(anyList())).thenAnswer(call -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            active.decrementAndGet();
            return List.of(new float[1024]);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.embed(List.of(id(1))));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch secondStarted = new CountDownLatch(1);
            var second = pool.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                service.embed(List.of(id(2)));
            });
            try {
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                await().atMost(3, TimeUnit.SECONDS)
                        .until(() -> maximum.get() > 1 || secondThread.get().getState() == Thread.State.BLOCKED);
                assertThat(maximum).hasValue(1);
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(maximum).hasValue(1);
        verify(client, times(2)).embed(anyList());
    }

    @Test
    void skipsIdAfterThreeFailuresInARow_AC_005_9(CapturedOutput output) {
        when(client.embed(anyList())).thenThrow(new AiUnavailableException());
        service.embed(List.of(id(1)));
        service.embed(List.of(id(1)));
        assertThat(service.skippedIds()).isEmpty();
        service.embed(List.of(id(1)));
        service.embed(List.of(id(1)));
        assertThat(service.skippedIds()).containsExactly(id(1));
        assertThat(output.toString().split("skipping embedding for ticket " + id(1), -1))
                .hasSize(2);
        assertThat(output).doesNotContain("private title");
    }

    @Test
    void resetsCountOnSuccessAndListenerStillEmbeds_AC_005_9() {
        when(client.embed(anyList())).thenThrow(new AiUnavailableException());
        for (int attempt = 0; attempt < 3; attempt++) {
            service.embed(List.of(id(1)));
        }
        assertThat(service.skippedIds()).containsExactly(id(1));
        doReturn(List.of(new float[1024])).when(client).embed(anyList());
        new TicketsPublishedListener(service, Runnable::run).on(new TicketsPublishedEvent(List.of(id(1))));
        assertThat(service.skippedIds()).isEmpty();
        verify(embeddings).upsert(eq(id(1)), any(), any());
        doThrow(new AiUnavailableException()).when(client).embed(anyList());
        service.embed(List.of(id(1)));
        service.embed(List.of(id(1)));
        assertThat(service.skippedIds()).isEmpty();
    }

    private SearchTicket ticket(UUID id) {
        SearchTicket ticket = mock(SearchTicket.class);
        when(ticket.getId()).thenReturn(id);
        when(ticket.getTitle()).thenReturn("private title");
        return ticket;
    }

    private static UUID id(long n) {
        return new UUID(0, n);
    }

    private static class Transactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
