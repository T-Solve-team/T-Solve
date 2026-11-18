package vn.tsolve.jiraapp.service;

import java.util.function.BiConsumer;
import vn.tsolve.jiraapp.dto.SolutionFormDraft;
import vn.tsolve.ticket.dto.SolutionSections;

/**
 * The Solution Form's synchronous draft and background rewrite, or a configured stand-in for demos.
 */
public interface SolutionFormAi {

    /**
     * The AI draft of the issue's Solution, or — when the AI is unavailable, too slow or gives an unusable answer — the
     * issue summary and description in Symptoms with the other sections empty (AC-002.1, AC-002.2). Never throws.
     */
    SolutionFormDraft draft(String summary, String description);

    /**
     * Starts the rewrite on the bounded AI pool, with its own deadline (AC-007.5).
     * Completion runs on that pool, with inline fallback if the pool is full or stopped.
     *
     * @throws java.util.concurrent.RejectedExecutionException when the pool is full
     */
    void startRewrite(SolutionSections sections, BiConsumer<SolutionSections, Throwable> completion);
}
