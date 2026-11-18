package vn.tsolve.jiraapp.service.impl;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import vn.tsolve.common.exception.MessageCodeException;

/** What the Ask T-Solve and Rewrite with AI jobs share, so the two flows cannot drift apart. */
final class JiraAppJobs {

    /** {@code ASK_DEADLINE_MS} in the Forge app's tsolveClient.js: the page stops polling after it. */
    static final Duration CLIENT_DEADLINE = Duration.ofSeconds(75);

    private JiraAppJobs() {}

    /** At most {@code threads} calls at once and as many waiting; any more are rejected at once (BUG-183). */
    static ThreadPoolExecutor pool(int threads, String setting, String prefix) {
        if (threads < 1) {
            throw new IllegalArgumentException(setting + " must be positive");
        }
        AtomicInteger number = new AtomicInteger();
        return new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threads),
                work -> {
                    Thread thread = new Thread(work, prefix + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** A job deadline the Forge page can still see: positive and at least 5 s under its 75 s polling deadline. */
    static Duration jobTimeout(Duration timeout, String setting) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(CLIENT_DEADLINE.minusSeconds(5)) > 0) {
            throw new IllegalArgumentException(setting + " must be positive and at most 70s (the Jira App polls 75s)");
        }
        return timeout;
    }

    /**
     * Like {@code supplyAsync}, but a timed-out future also cancels its task: a queued one is skipped and a running
     * one is interrupted, so a job past its deadline does not keep a pool thread.
     */
    // ponytail: the interrupt frees the thread only if the AI client honours it; a blocking read that ignores it
    // still runs to its own HTTP timeout.
    static <T> CompletableFuture<T> supply(Supplier<T> call, Executor executor) {
        CompletableFuture<T> future = new CompletableFuture<>();
        FutureTask<Void> task = new FutureTask<>(
                () -> {
                    try {
                        future.complete(call.get());
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    }
                },
                null);
        executor.execute(task);
        future.whenComplete((result, error) -> {
            if (error instanceof TimeoutException && task.cancel(true) && executor instanceof ThreadPoolExecutor pool) {
                pool.purge();
            }
        });
        return future;
    }

    /** The SRS code of a refusal (a 4xx MessageCodeException, e.g. MSG08); none for a timeout or a failure. */
    static String refusalCode(Throwable error) {
        Throwable cause = unwrap(error);
        return cause instanceof MessageCodeException refusal
                        && refusal.getStatus().is4xxClientError()
                ? refusal.getCode()
                : null;
    }

    /** Only the error type: a message or stack could carry question, answer or Solution text (NF 3). */
    static void logFailure(Logger log, String job, UUID id, Throwable error) {
        log.warn("{} {}: error type {}", job, id, unwrap(error).getClass().getSimpleName());
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
