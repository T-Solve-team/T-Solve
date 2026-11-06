package vn.tsolve.tracker.service.impl;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.service.SuggestionJobQueue;
import vn.tsolve.tracker.service.SuggestionJobService;

/**
 * Stand-in job runner until the {@code worker} module's JobRunr exists (KAN-161); replace this class with a JobRunr
 * adapter then. Jobs run on a small pool owned by this class — not a Spring bean, so Boot's application executor is
 * left alone. Queued jobs are lost on restart (accepted for the stand-in, design STORY-011).
 */
@Service
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
        try {
            executor.execute(() -> jobService.run(request));
        } catch (RejectedExecutionException e) {
            log.warn("Suggestion job dropped, queue full: {}", request.issueKey());
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
