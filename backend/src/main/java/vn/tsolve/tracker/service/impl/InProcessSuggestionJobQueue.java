package vn.tsolve.tracker.service.impl;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.exception.SuggestionJobFailedException;
import vn.tsolve.tracker.service.SuggestionJobQueue;
import vn.tsolve.tracker.service.SuggestionJobService;

/**
 * Runs the suggestion job in this process while the worker is not enabled (AC-011.9): no retry, and queued jobs are
 * lost on restart. Jobs run on a small pool owned by this class — not a Spring bean, so Boot's application executor
 * is left alone.
 */
@Service
@ConditionalOnProperty(prefix = "tsolve.worker", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InProcessSuggestionJobQueue implements SuggestionJobQueue, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InProcessSuggestionJobQueue.class);

    static final int THREADS = 2;
    static final int QUEUE_CAPACITY = 100;

    private final SuggestionJobService jobService;
    private final Executor executor;
    private final ThreadPoolTaskExecutor ownPool;

    @Autowired
    public InProcessSuggestionJobQueue(SuggestionJobService jobService) {
        this.jobService = jobService;
        this.ownPool = pool();
        this.executor = ownPool;
    }

    InProcessSuggestionJobQueue(SuggestionJobService jobService, Executor executor) {
        this.jobService = jobService;
        this.ownPool = null;
        this.executor = executor;
    }

    @Override
    public void enqueue(SuggestionJobRequest request) {
        // The task only describes the work; execute() puts it on the pool's queue and returns at once, and a
        // suggestion-job-* thread runs it, so the request is answered without waiting for the job (AC-011.9).
        Runnable task = new SuggestionTask(request);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("Suggestion job dropped, queue full: {}", request.issueKey());
        }
    }

    /** One suggestion job, run by a pool thread rather than the thread that queued it. */
    private final class SuggestionTask implements Runnable {

        private final SuggestionJobRequest request;

        private SuggestionTask(SuggestionJobRequest request) {
            this.request = request;
        }

        @Override
        public void run() {
            runOnce(request);
        }
    }

    // The job already logged its failure (key and error type); without the worker there is no retry.
    private void runOnce(SuggestionJobRequest request) {
        try {
            jobService.run(request.suggestionId());
        } catch (RuntimeException e) {
            String reason = e instanceof SuggestionJobFailedException
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            log.warn("Suggestion job failed, not retried: {} ({})", request.issueKey(), reason);
        }
    }

    @Override
    public void destroy() {
        if (ownPool != null) {
            ownPool.shutdown();
        }
    }

    private static ThreadPoolTaskExecutor pool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(THREADS);
        pool.setMaxPoolSize(THREADS);
        pool.setQueueCapacity(QUEUE_CAPACITY);
        pool.setThreadNamePrefix("suggestion-job-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(30);
        pool.initialize();
        return pool;
    }
}
