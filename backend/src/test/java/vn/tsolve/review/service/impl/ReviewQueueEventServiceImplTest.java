package vn.tsolve.review.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.tsolve.identity.signin.port.TenantScope;

class ReviewQueueEventServiceImplTest {

    private static final UUID WORKSPACE = new UUID(0, 1);
    private static final UUID PROJECT = new UUID(0, 2);

    private final BlockingQueue<PGNotification> notifications = new LinkedBlockingQueue<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private ReviewQueueEventServiceImpl service;

    @AfterEach
    void stop() {
        release.countDown();
        if (service != null) {
            service.shutdown();
        }
    }

    /** Records what it was sent; when {@code blocking}, a send never returns until the test releases it. */
    private final class FakeEmitter extends SseEmitter {
        private final boolean blocking;
        private final List<String> sent = new CopyOnWriteArrayList<>();

        FakeEmitter(boolean blocking) {
            super(0L);
            this.blocking = blocking;
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            if (blocking) {
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
            builder.build().forEach(data -> sent.add(String.valueOf(data.getData())));
        }
    }

    @Test
    void edge10_aBlockedStreamDoesNotDelayAnotherStreamsEvent_AC_012_2() throws Exception {
        var slow = new FakeEmitter(true);
        var fast = new FakeEmitter(false);
        var emitters = new CopyOnWriteArrayList<>(List.of(slow, fast));
        service = serviceWith(emitters);

        service.subscribe(WORKSPACE, new UUID(0, 3), Set.of(PROJECT));
        service.subscribe(WORKSPACE, new UUID(0, 4), Set.of(PROJECT));
        notifications.add(notification(PROJECT.toString()));

        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline
                && fast.sent.stream().noneMatch(s -> s.contains(PROJECT.toString()))) {
            Thread.sleep(20);
        }
        assertThat(fast.sent).anyMatch(s -> s.contains(PROJECT.toString()));
        assertThat(slow.sent).isEmpty();
    }

    private ReviewQueueEventServiceImpl serviceWith(List<FakeEmitter> emitters) throws Exception {
        var connection = mock(Connection.class);
        var pg = mock(PGConnection.class);
        var statement = mock(Statement.class);
        var prepared = mock(PreparedStatement.class);
        AtomicReference<String> token = new AtomicReference<>();
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        when(connection.unwrap(PGConnection.class)).thenReturn(pg);
        doAnswer(call -> {
                    token.set(call.getArgument(1));
                    return null;
                })
                .when(prepared)
                .setString(eq(2), anyString());
        // the join barrier's own NOTIFY comes back through the LISTEN connection
        when(prepared.execute()).thenAnswer(call -> {
            notifications.add(notification(token.get()));
            return true;
        });
        when(pg.getNotifications(anyInt())).thenAnswer(call -> {
            PGNotification next = notifications.poll(50, TimeUnit.MILLISECONDS);
            return next == null ? null : new PGNotification[] {next};
        });
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        TenantScope tenant = new TenantScope() {
            @Override
            public UUID currentWorkspaceId() {
                return WORKSPACE;
            }

            @Override
            public <T> T runIn(UUID workspaceId, java.util.function.Supplier<T> work) {
                return work.get();
            }
        };
        var next = new java.util.concurrent.atomic.AtomicInteger();
        return new ReviewQueueEventServiceImpl(
                dataSource,
                tenant,
                Optional.empty(),
                Duration.ofHours(1),
                timeout -> emitters.get(next.getAndIncrement()));
    }

    private static PGNotification notification(String parameter) {
        var notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn(parameter);
        return notification;
    }
}
