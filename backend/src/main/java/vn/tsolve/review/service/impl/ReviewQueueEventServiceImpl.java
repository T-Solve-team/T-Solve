package vn.tsolve.review.service.impl;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.review.service.ReviewQueueEventService;
import vn.tsolve.review.service.ReviewWorkspaceAccess;

@Service
public class ReviewQueueEventServiceImpl implements ReviewQueueEventService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewQueueEventServiceImpl.class);
    private static final String CHANNEL = "review_queue";
    private static final String EVENT = "queue-changed";
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private static final int POLL_MS = 500;
    private static final long RETRY_MS = 2_000;
    private static final long BARRIER_WAIT_MS = 2_000;
    private static final long LISTEN_WAIT_MS = 5_000;
    private static final int SEND_THREADS = 4;

    private final DataSource dataSource;
    private final TenantScope tenant;
    private final Optional<ReviewWorkspaceAccess> workspaceAccess;
    private final Duration heartbeat;
    private final LongFunction<SseEmitter> emitters;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "review-queue-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService sends = Executors.newFixedThreadPool(SEND_THREADS, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "review-queue-send-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });
    private final Object lock = new Object();
    private final Map<UUID, Listener> listeners = new HashMap<>();

    @Autowired
    public ReviewQueueEventServiceImpl(
            DataSource dataSource,
            TenantScope tenant,
            Optional<ReviewWorkspaceAccess> workspaceAccess,
            @Value("${tsolve.review.events.heartbeat:PT25S}") Duration heartbeat) {
        this(dataSource, tenant, workspaceAccess, heartbeat, SseEmitter::new);
    }

    ReviewQueueEventServiceImpl(
            DataSource dataSource,
            TenantScope tenant,
            Optional<ReviewWorkspaceAccess> workspaceAccess,
            Duration heartbeat,
            LongFunction<SseEmitter> emitters) {
        this.dataSource = dataSource;
        this.tenant = tenant;
        this.workspaceAccess = workspaceAccess;
        this.heartbeat = heartbeat;
        this.emitters = emitters;
    }

    @Override
    public SseEmitter subscribe(UUID workspaceId, UUID accountId, Set<UUID> managedProjectIds) {
        var subscription = new Subscription(
                workspaceId, accountId, emitters.apply(EMITTER_TIMEOUT_MS), Set.copyOf(managedProjectIds));
        Listener listener;
        boolean joined;
        // no JDBC call under the lock: the listener thread opens its own connection
        synchronized (lock) {
            listener = listeners.get(workspaceId);
            joined = listener != null;
            if (!joined) {
                listener = new Listener(workspaceId);
                listeners.put(workspaceId, listener);
                subscription.active = true;
            }
            subscription.listener = listener;
            listener.add(subscription);
            if (!joined) {
                listener.start();
            }
        }
        if (joined) {
            listener.awaitBarrier(subscription);
        } else {
            // the first heartbeat only comes after the LISTEN is attached
            listener.awaitListening();
        }
        subscription.emitter.onCompletion(() -> subscription.end(false));
        subscription.emitter.onTimeout(() -> subscription.end(false));
        subscription.emitter.onError(error -> subscription.end(false));
        subscription.heartbeat = heartbeats.scheduleWithFixedDelay(
                subscription::enqueueHeartbeat, heartbeat.toMillis(), heartbeat.toMillis(), TimeUnit.MILLISECONDS);
        // commits the response headers (text/event-stream) as soon as the stream opens
        subscription.enqueueOpen();
        return subscription.emitter;
    }

    private void unsubscribe(Subscription subscription) {
        synchronized (lock) {
            Listener listener = subscription.listener;
            if (listener != null && listener.remove(subscription)) {
                if (listeners.get(subscription.workspaceId) == listener) {
                    listeners.remove(subscription.workspaceId);
                }
                listener.close();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeats.shutdownNow();
        sends.shutdownNow();
        synchronized (lock) {
            listeners.values().forEach(Listener::close);
            listeners.clear();
        }
    }

    /** What still has to be sent is kept here and drained by the shared send pool; callers only enqueue. */
    private final class Subscription {
        private final UUID workspaceId;
        private final UUID accountId;
        private final SseEmitter emitter;
        private final CountDownLatch ready = new CountDownLatch(1);
        private final Set<UUID> pending = new LinkedHashSet<>();
        private volatile Set<UUID> projects;
        private volatile boolean active;
        private volatile Listener listener;
        private volatile ScheduledFuture<?> heartbeat;
        private boolean ended;
        private boolean pendingOpen;
        private boolean pendingHeartbeat;
        private boolean draining;

        Subscription(UUID workspaceId, UUID accountId, SseEmitter emitter, Set<UUID> projects) {
            this.workspaceId = workspaceId;
            this.accountId = accountId;
            this.emitter = emitter;
            this.projects = projects;
        }

        boolean watches(UUID project) {
            return projects.contains(project);
        }

        void enqueueProject(UUID project) {
            synchronized (this) {
                if (ended || !projects.contains(project)) {
                    return;
                }
                pending.add(project);
            }
            schedule();
        }

        void enqueueHeartbeat() {
            synchronized (this) {
                if (ended) {
                    cancelHeartbeat();
                    return;
                }
                pendingHeartbeat = true;
            }
            schedule();
        }

        void enqueueOpen() {
            synchronized (this) {
                if (ended) {
                    return;
                }
                pendingOpen = true;
            }
            schedule();
        }

        private void schedule() {
            synchronized (this) {
                if (draining || ended) {
                    return;
                }
                draining = true;
            }
            try {
                sends.execute(this::drain);
            } catch (RejectedExecutionException shuttingDown) {
                end(true);
            }
        }

        private void drain() {
            while (true) {
                boolean open;
                boolean beat;
                List<UUID> changed;
                synchronized (this) {
                    if (ended || (!pendingOpen && !pendingHeartbeat && pending.isEmpty())) {
                        draining = false;
                        return;
                    }
                    open = pendingOpen;
                    beat = pendingHeartbeat;
                    changed = List.copyOf(pending);
                    pendingOpen = false;
                    pendingHeartbeat = false;
                    pending.clear();
                }
                if (open && !send(SseEmitter.event().comment("open"))) {
                    return;
                }
                if (beat) {
                    if (!stillManaged()) {
                        end(true);
                        return;
                    }
                    if (!send(SseEmitter.event().comment("hb"))) {
                        return;
                    }
                }
                for (UUID project : changed) {
                    if (projects.contains(project)
                            && !send(SseEmitter.event().name(EVENT).data("{\"projectId\":\"" + project + "\"}"))) {
                        return;
                    }
                }
            }
        }

        private boolean send(SseEmitter.SseEventBuilder event) {
            try {
                emitter.send(event);
                return true;
            } catch (IOException | RuntimeException e) {
                end(true);
                return false;
            }
        }

        /** Re-reads the managed Projects once per heartbeat; false when nothing is left or the Account is unverified. */
        private boolean stillManaged() {
            ReviewWorkspaceAccess access = workspaceAccess.orElse(null);
            if (access == null) {
                return true;
            }
            Optional<Set<UUID>> managed;
            try {
                managed = tenant.runIn(workspaceId, () -> access.managedProjectIds(accountId));
            } catch (RuntimeException unavailable) {
                return true; // keep the stream until the next heartbeat can tell
            }
            if (managed.isEmpty()) {
                return false;
            }
            Set<UUID> kept = projects.stream().filter(managed.get()::contains).collect(Collectors.toUnmodifiableSet());
            if (kept.isEmpty()) {
                return false;
            }
            projects = kept;
            return true;
        }

        void end(boolean complete) {
            synchronized (this) {
                if (ended) {
                    return;
                }
                ended = true;
                pending.clear();
            }
            cancelHeartbeat();
            unsubscribe(this);
            if (complete) {
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // the client is already gone
                }
            }
        }

        private void cancelHeartbeat() {
            ScheduledFuture<?> future = heartbeat;
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    /** One LISTEN connection for a Workspace while at least one emitter is open; its thread owns the connection. */
    private final class Listener implements Runnable {
        private final UUID workspaceId;
        private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
        private final Map<String, Subscription> barriers = new ConcurrentHashMap<>();
        private final CountDownLatch listening = new CountDownLatch(1);
        private volatile boolean closed;
        private Connection connection;
        private boolean reopened;
        private boolean failing;

        Listener(UUID workspaceId) {
            this.workspaceId = workspaceId;
        }

        void start() {
            Thread thread = new Thread(this, "review-queue-listener");
            thread.setDaemon(true);
            thread.start();
        }

        void add(Subscription subscription) {
            subscriptions.add(subscription);
        }

        void awaitListening() {
            try {
                listening.await(LISTEN_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
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
                awaitListening();
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
            return subscriptions.remove(subscription) && subscriptions.isEmpty();
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
            listening.countDown();
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
                    subscription.enqueueProject(project);
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
                if (subscription.active && subscription.watches(project)) {
                    subscription.enqueueProject(project);
                }
            }
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
