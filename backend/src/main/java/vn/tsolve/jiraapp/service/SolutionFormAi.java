package vn.tsolve.jiraapp.service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

    /**
     * Drafts the Solution of an issue that moved to Done on the rewrite's pool and deadline (AC-005.11): reads the
     * comments there, asks the ai module, and passes the sections to {@code onAiDraft} only for an AI draft that
     * finished in time; a fallback, failure or timeout passes nothing (AC-005.12). Only with the real AI.
     *
     * @throws java.util.concurrent.RejectedExecutionException when the pool is full
     */
    void startDraft(
            String summary, String description, Supplier<List<String>> comments, Consumer<SolutionSections> onAiDraft);
}
