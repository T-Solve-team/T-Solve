package vn.tsolve.review.service.impl;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewQueueEventService;

@Service
public class ReviewQueueEventServiceImpl implements ReviewQueueEventService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewQueueEventServiceImpl.class);
    private static final String CHANNEL = "review_queue";
    private static final String EVENT = "queue-changed";
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private static final int POLL_MS = 500;
    private static final long RETRY_MS = 2_000;
    private static final long BARRIER_WAIT_MS = 2_000;

    private final DataSource dataSource;
    private final TenantScope tenant;
    private final Duration heartbeat;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "review-queue-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final Object lock = new Object();
    private final Map<UUID, Listener> listeners = new HashMap<>();

    public ReviewQueueEventServiceImpl(
            DataSource dataSource,
            TenantScope tenant,
            @Value("${tsolve.review.events.heartbeat:PT25S}") Duration heartbeat) {
        this.dataSource = dataSource;
        this.tenant = tenant;
        this.heartbeat = heartbeat;
    }

    @Override
    public SseEmitter subscribe(UUID workspaceId, Set<UUID> managedProjectIds) {
        var subscription = new Subscription(new SseEmitter(EMITTER_TIMEOUT_MS), Set.copyOf(managedProjectIds));
        Listener listener;
        boolean joined;
        synchronized (lock) {
            // the LISTEN is attached here, before any heartbeat can be scheduled
            listener = listeners.get(workspaceId);
            joined = listener != null;
            if (!joined) {
                listener = new Listener(workspaceId);
                listeners.put(workspaceId, listener);
                subscription.active = true;
            }
            listener.add(subscription);
        }
        if (joined) {
            listener.awaitBarrier(subscription);
        }
        Runnable leave = () -> unsubscribe(workspaceId, subscription);
        subscription.emitter.onCompletion(leave);
        subscription.emitter.onTimeout(leave);
        subscription.emitter.onError(error -> leave.run());
        subscription.heartbeat = heartbeats.scheduleWithFixedDelay(
                () -> subscription.send(SseEmitter.event().comment("hb"), leave),
                heartbeat.toMillis(),
                heartbeat.toMillis(),
                TimeUnit.MILLISECONDS);
        // commits the response headers (text/event-stream) as soon as the stream opens
        subscription.send(SseEmitter.event().comment("open"), leave);
        return subscription.emitter;
    }

    private void unsubscribe(UUID workspaceId, Subscription subscription) {
        subscription.cancel();
        synchronized (lock) {
            Listener listener = listeners.get(workspaceId);
            if (listener != null && listener.remove(subscription)) {
                listeners.remove(workspaceId);
                listener.close();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeats.shutdownNow();
        synchronized (lock) {
            listeners.values().forEach(Listener::close);
            listeners.clear();
        }
    }

    private final class Subscription {
        private final SseEmitter emitter;
        private final Set<UUID> projects;
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile boolean active;
        private volatile ScheduledFuture<?> heartbeat;

        Subscription(SseEmitter emitter, Set<UUID> projects) {
            this.emitter = emitter;
            this.projects = projects;
        }

        void send(SseEmitter.SseEventBuilder event, Runnable onFailure) {
            try {
                emitter.send(event);
            } catch (IOException | RuntimeException e) {
                onFailure.run();
            }
        }

        void cancel() {
            ScheduledFuture<?> future = heartbeat;
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    /** One LISTEN connection for a Workspace while at least one emitter is open. */
    private final class Listener implements Runnable {
        private final UUID workspaceId;
        private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
        private final Map<String, Subscription> barriers = new ConcurrentHashMap<>();
        private volatile boolean closed;
        private Connection connection;
        private boolean reopened;
        private boolean failing;

        Listener(UUID workspaceId) {
            this.workspaceId = workspaceId;
            try {
                connect();
            } catch (SQLException | RuntimeException e) {
                fail(e);
            }
            Thread thread = new Thread(this, "review-queue-listener");
            thread.setDaemon(true);
            thread.start();
        }

        void add(Subscription subscription) {
            subscriptions.add(subscription);
        }

        /**
         * A subscription joining a running listener must not see changes committed before it joined, though their
         * notifications may still be on their way. It starts receiving once its own NOTIFY (committed after those)
         * comes back; if that does not happen soon, it starts anyway.
         */
        void awaitBarrier(Subscription subscription) {
            String token = "barrier-" + UUID.randomUUID();
            barriers.put(token, subscription);
            try {
                tenant.runIn(workspaceId, () -> {
                    try (Connection other = dataSource.getConnection();
                            PreparedStatement statement = other.prepareStatement("SELECT pg_notify(?, ?)")) {
                        statement.setString(1, CHANNEL);
                        statement.setString(2, token);
                        statement.execute();
                        return null;
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                });
                subscription.ready.await(BARRIER_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException unavailable) {
                // the listener is reconnecting; its resync below covers this subscription
            } finally {
                barriers.remove(token);
                subscription.active = true;
            }
        }

        /** Returns true when this was the last emitter. */
        boolean remove(Subscription subscription) {
            subscriptions.remove(subscription);
            return subscriptions.isEmpty();
        }

        void close() {
            closed = true;
        }

        @Override
        public void run() {
            while (!closed) {
                try {
                    if (connection == null) {
                        connect();
                    }
                    if (reopened) {
                        reopened = false;
                        failing = false;
                        notifyEveryProject();
                    }
                    PGNotification[] received =
                            connection.unwrap(PGConnection.class).getNotifications(POLL_MS);
                    if (received != null) {
                        for (PGNotification notification : received) {
                            dispatch(notification.getParameter());
                        }
                    }
                } catch (SQLException | RuntimeException e) {
                    fail(e);
                    pause();
                }
            }
            release();
        }

        private void connect() throws SQLException {
            Connection opened = tenant.runIn(workspaceId, () -> {
                try {
                    return dataSource.getConnection();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            try (Statement statement = opened.createStatement()) {
                statement.execute("LISTEN " + CHANNEL);
            } catch (SQLException | RuntimeException e) {
                closeQuietly(opened);
                throw e;
            }
            connection = opened;
        }

        private void fail(Exception e) {
            if (!failing) {
                failing = true;
                // no payload and no ids: only the kind of failure
                LOG.warn(
                        "Review Queue listener lost its database connection ({}); retrying",
                        e.getClass().getSimpleName());
            }
            release();
            reopened = true;
        }

        private void pause() {
            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                closed = true;
            }
        }

        private void release() {
            Connection old = connection;
            connection = null;
            if (old != null) {
                try (Statement statement = old.createStatement()) {
                    statement.execute("UNLISTEN *");
                } catch (SQLException | RuntimeException ignored) {
                    // the connection is closed next either way
                }
                closeQuietly(old);
            }
        }

        private void notifyEveryProject() {
            for (Subscription subscription : subscriptions) {
                for (UUID project : subscription.projects) {
                    publish(subscription, project);
                }
            }
        }

        private void dispatch(String payload) {
            Subscription waiting = payload == null ? null : barriers.get(payload);
            if (waiting != null) {
                waiting.active = true;
                waiting.ready.countDown();
                return;
            }
            UUID project;
            try {
                project = UUID.fromString(payload);
            } catch (IllegalArgumentException | NullPointerException notAProject) {
                return;
            }
            for (Subscription subscription : subscriptions) {
                if (subscription.active && subscription.projects.contains(project)) {
                    publish(subscription, project);
                }
            }
        }

        private void publish(Subscription subscription, UUID project) {
            subscription.send(
                    SseEmitter.event().name(EVENT).data("{\"projectId\":\"" + project + "\"}"),
                    () -> unsubscribe(workspaceId, subscription));
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // nothing left to release
        }
    }
}
