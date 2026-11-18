package vn.tsolve.jiraapp.service.impl;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Short job-store writes must not wait behind AI calls. This is not an Executor bean, so Boot keeps its own. */
@Component
final class JiraAppJobCompletions {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
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

    Executor executor() {
        return executor;
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
