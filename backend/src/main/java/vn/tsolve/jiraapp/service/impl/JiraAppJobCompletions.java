package vn.tsolve.jiraapp.service.impl;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Short job-store writes must not wait behind AI calls. This is not an Executor bean, so Boot keeps its own.
 * Stopped before the job store (DependsOn), so the writes queued at shutdown still reach Redis.
 */
@Component
@DependsOn("redisAskJobStore")
final class JiraAppJobCompletions {

    private static final Logger log = LoggerFactory.getLogger(JiraAppJobCompletions.class);

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1_000),
            work -> {
                Thread thread = new Thread(work, "jira-app-job-completion");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    ThreadPoolExecutor pool() {
        return pool;
    }

    Executor executor() {
        return withFallback(pool, Thread::startVirtualThread);
    }

    /**
     * A full or stopped pool hands the write to {@code fallback}, never to the caller's thread: a deadline completes
     * the job on the JVM-wide CompletableFuture delay thread, which a slow Redis write would stall for every timer.
     */
    static Executor withFallback(Executor pool, Executor fallback) {
        return task -> {
            try {
                pool.execute(task);
            } catch (RejectedExecutionException e) {
                log.warn("Jira App job completion pool full or stopped: error type RejectedExecutionException");
                fallback.execute(task);
            }
        };
    }

    // Lets the queued writes finish; shutdownNow would drop them and leave their jobs PENDING until the TTL.
    @PreDestroy
    void stop() throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            log.warn(
                    "Jira App job completions: {} writes dropped at shutdown",
                    pool.shutdownNow().size());
        }
    }
}
