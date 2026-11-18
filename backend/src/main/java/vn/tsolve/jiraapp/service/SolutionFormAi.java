package vn.tsolve.jiraapp.service;

import java.util.function.BiConsumer;
import vn.tsolve.ticket.dto.SolutionSections;

/**
 * The Solution Form's background rewrite (no AI draft since KAN-371), or a configured stand-in for demos.
 */
public interface SolutionFormAi {

    /**
     * Starts the rewrite on the bounded AI pool, with its own deadline (AC-007.5).
     * Completion runs on that pool, with inline fallback if the pool is full or stopped.
     *
     * @throws java.util.concurrent.RejectedExecutionException when the pool is full
     */
    void startRewrite(SolutionSections sections, BiConsumer<SolutionSections, Throwable> completion);
}
