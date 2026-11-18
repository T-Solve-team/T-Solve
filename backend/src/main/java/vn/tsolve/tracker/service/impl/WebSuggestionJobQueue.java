package vn.tsolve.tracker.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import vn.tsolve.tracker.dto.SuggestionJobRequest;
import vn.tsolve.tracker.service.SuggestionJobQueue;
import vn.tsolve.worker.service.WebJobQueue;

/**
 * Hands the suggestion job to the worker process from a web process that runs no JobRunr (AC-011.10, BUG-342); the
 * worker runs it in the Workspace and retries it (AC-011.6). Never throws: an unqueued job is logged (AC-011.11).
 */
@Service
@ConditionalOnExpression(
        "'${tsolve.worker.web-jobs-enabled:false}' == 'true' and '${tsolve.worker.enabled:false}' != 'true'")
public class WebSuggestionJobQueue implements SuggestionJobQueue {

    private static final Logger log = LoggerFactory.getLogger(WebSuggestionJobQueue.class);

    private final WebJobQueue jobs;

    public WebSuggestionJobQueue(WebJobQueue jobs) {
        this.jobs = jobs;
    }

    @Override
    public void enqueue(SuggestionJobRequest request) {
        try {
            jobs.enqueue(SuggestionJobHandler.TYPE, request.suggestionId());
        } catch (RuntimeException e) {
            log.warn(
                    "Suggestion job not queued for {}: {}",
                    request.issueKey(),
                    e.getClass().getSimpleName());
        }
    }
}
