package vn.tsolve.review.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewWorkspaceAccess;

class ReviewQueueEventServiceImplTest {

    private static final UUID WORKSPACE = new UUID(0, 1);
    private static final UUID PROJECT = new UUID(0, 2);

    private final BlockingQueue<PGNotification> notifications = new LinkedBlockingQueue<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private ReviewQueueEventServiceImpl service;
    private Connection connection;
    private DataSource dataSource;

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
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private boolean failOnRelease;
        private Error error;
        private Runnable completionCallback = () -> {};

        FakeEmitter(boolean blocking) {
            super(0L);
            this.blocking = blocking;
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            entered.countDown();
            if (error != null) {
                throw error;
            }
            if (blocking) {
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("test send timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
                if (failOnRelease) {
                    throw new IOException("test socket closed");
                }
            }
            builder.build().forEach(data -> sent.add(String.valueOf(data.getData())));
        }

        @Override
        public void onCompletion(Runnable callback) {
            completionCallback = callback;
            super.onCompletion(callback);
        }

        @Override
        public void complete() {
            completed.countDown();
            super.complete();
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

        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
                && fast.sent.stream().noneMatch(s -> s.contains(PROJECT.toString()))) {
            Thread.sleep(20);
        }
        assertThat(fast.sent).anyMatch(s -> s.contains(PROJECT.toString()));
        assertThat(slow.sent).isEmpty();
    }

    @Test
    void addedProjectGetsQueueChangedAtTheHeartbeat_AC_012_2_BUG_174() throws Exception {
        UUID added = new UUID(0, 5);
        var managed = new AtomicReference<>(Set.of(PROJECT));
        var access = mock(ReviewWorkspaceAccess.class);
        when(access.managedProjectIds(any(UUID.class))).thenAnswer(call -> Optional.of(managed.get()));
        var emitter = new FakeEmitter(false);
        service = serviceWith(List.of(emitter), Optional.of(access), Duration.ofMillis(100));
        service.subscribe(WORKSPACE, new UUID(0, 3), Set.of(PROJECT));
        awaitUntil(() -> heartbeatCount(emitter) > 0);

        managed.set(Set.of(PROJECT, added));
        awaitUntil(() -> emitter.sent.stream().anyMatch(s -> s.contains(added.toString())));
        int beats = heartbeatCount(emitter);
        awaitUntil(() -> heartbeatCount(emitter) >= beats + 2);
        assertThat(emitter.sent.stream().filter(s -> s.contains(added.toString())))
                .hasSize(1);
        assertThat(emitter.sent).noneMatch(s -> s.contains(PROJECT.toString()));
    }

    @Test
    void subscribeFailureAfterAddRemovesTheSubscription_BUG_177() throws Exception {
        service = serviceWith(List.of(new FakeEmitter(false)), Optional.empty(), Duration.ZERO);
        assertThatThrownBy(() -> service.subscribe(WORKSPACE, new UUID(0, 3), Set.of(PROJECT)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(connection, timeout(10_000)).close();
    }

    @Test
    void joiningSubscriptionHasOneFiveSecondWaitBudget_BUG_177() throws Exception {
        service = serviceWith(List.of(new FakeEmitter(false), new FakeEmitter(false)));
        service.subscribe(WORKSPACE, new UUID(0, 3), Set.of(PROJECT));
        var interrupted = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(call -> {
            try {
                release.await();
                return connection;
            } catch (InterruptedException cancellation) {
                interrupted.countDown();
                throw new java.sql.SQLException(cancellation);
            }
        });

        long started = System.nanoTime();
        service.subscribe(WORKSPACE, new UUID(0, 4), Set.of(PROJECT));
        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(6));
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void joinBarrierFailureRemovesOnlyTheJoiningSubscription_BUG_177() throws Exception {
        var original = new FakeEmitter(false);
        service = serviceWith(List.of(original, new FakeEmitter(false)));
        service.subscribe(WORKSPACE, new UUID(0, 3), Set.of(PROJECT));
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("barrier unavailable"));

        assertThatThrownBy(() -> service.subscribe(WORKSPACE, new UUID(0, 4), Set.of(PROJECT)))
                .isInstanceOf(IllegalStateException.class);
        // The original stream still receives events and owns the listener.
        verify(connection, org.mockito.Mockito.never()).close();
        notifications.add(notification(PROJECT.toString()));
        awaitUntil(() -> original.sent.stream().anyMatch(s -> s.contains(PROJECT.toString())));
        original.completionCallback.run();
        verify(connection, timeout(10_000)).close();
    }

    @Test
    void projectGrantedMidStreamReceivesEvents_AC_012_2_BUG_172() throws Exception {
        UUID second = new UUID(0, 5);
        var managed = new AtomicReference<>(Set.of(PROJECT));
        var access = mock(ReviewWorkspaceAccess.class);
        when(access.managedProjectIds(any(UUID.class))).thenAnswer(call -> Optional.of(managed.get()));
        var emitter = new FakeEmitter(false);
        service = serviceWith(List.of(emitter), Optional.of(access), Duration.ofMillis(100));
        service.subscribe(WORKSPACE, new UUID(0, 3), Set.of(PROJECT));
        awaitUntil(() -> emitter.sent.stream().anyMatch(s -> s.contains(":hb")));

        int beats = heartbeatCount(emitter);
        managed.set(Set.of(PROJECT, second));
        awaitUntil(() -> heartbeatCount(emitter) >= beats + 2);
        notifications.add(notification(second.toString()));
        notifications.add(notification(PROJECT.toString()));
        awaitUntil(() -> emitter.sent.stream().anyMatch(s -> s.contains(second.toString()))
                && emitter.sent.stream().anyMatch(s -> s.contains(PROJECT.toString())));

        managed.set(Set.of());
        assertThat(emitter.completed.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void drainRecoversAfterAnUnexpectedError_BUG_173() throws Exception {
        var broken = new FakeEmitter(false);
        broken.error = new AssertionError("unexpected send failure");
        var healthy = new FakeEmitter(false);
        service = serviceWith(List.of(broken, healthy));
        service.subscribe(WORKSPACE, new UUID(0, 3), Set.of(PROJECT));
        service.subscribe(WORKSPACE, new UUID(0, 4), Set.of(PROJECT));
        assertThat(broken.completed.await(10, TimeUnit.SECONDS)).isTrue();
        notifications.add(notification(PROJECT.toString()));
        awaitUntil(() -> healthy.sent.stream().anyMatch(s -> s.contains(PROJECT.toString())));
    }

    @Test
    void sendPoolSaturatedByStuckStreamsLosesNoEventAndDoesNotBlockHeartbeats_AC_012_2() throws Exception {
        var emitters = new CopyOnWriteArrayList<FakeEmitter>();
        for (int i = 0; i < 4; i++) {
            var blocked = new FakeEmitter(true);
            blocked.failOnRelease = true;
            emitters.add(blocked);
        }
        var healthy = new FakeEmitter(false);
        emitters.add(healthy);
        emitters.add(new FakeEmitter(false));
        var access = mock(ReviewWorkspaceAccess.class);
        var checked = new CountDownLatch(1);
        when(access.managedProjectIds(any(UUID.class))).thenAnswer(call -> {
            if (new UUID(0, 20).equals(call.getArgument(0))) {
                checked.countDown();
            }
            return Optional.of(Set.of(PROJECT));
        });
        service = serviceWith(emitters, Optional.of(access), Duration.ofMillis(100));
        for (int i = 0; i < 4; i++) {
            service.subscribe(WORKSPACE, new UUID(0, 10 + i), Set.of(PROJECT));
            assertThat(emitters.get(i).entered.await(1, TimeUnit.SECONDS)).isTrue();
        }
        service.subscribe(WORKSPACE, new UUID(0, 20), Set.of(PROJECT));
        notifications.add(notification(PROJECT.toString()));
        long started = System.nanoTime();
        // Its barrier is behind the project notification, proving listener dispatch also remained responsive.
        service.subscribe(WORKSPACE, new UUID(0, 21), Set.of(PROJECT));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(checked.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(healthy.sent).isEmpty();

        release.countDown();
        awaitUntil(() ->
                healthy.sent.stream().anyMatch(s -> s.contains(PROJECT.toString())) && heartbeatCount(healthy) > 0);
        for (int i = 0; i < 4; i++) {
            assertThat(emitters.get(i).completed.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static int heartbeatCount(FakeEmitter emitter) {
        return (int) emitter.sent.stream().filter(s -> s.contains(":hb")).count();
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private ReviewQueueEventServiceImpl serviceWith(List<FakeEmitter> emitters) throws Exception {
        return serviceWith(emitters, Optional.empty(), Duration.ofHours(1));
    }

    private ReviewQueueEventServiceImpl serviceWith(
            List<FakeEmitter> emitters, Optional<ReviewWorkspaceAccess> access, Duration heartbeat) throws Exception {
        connection = mock(Connection.class);
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
        dataSource = mock(DataSource.class);
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
                dataSource, tenant, access, heartbeat, timeout -> emitters.get(next.getAndIncrement()));
    }

    private static PGNotification notification(String parameter) {
        var notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn(parameter);
        return notification;
    }
}
