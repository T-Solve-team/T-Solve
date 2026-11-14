package vn.tsolve.jiraapp.service;

import vn.tsolve.jiraapp.dto.SolutionFormDraft;
import vn.tsolve.ticket.dto.SolutionSections;

/**
 * The AI of the Solution Form, within the Jira App's time budget (Forge allows 25 s, the UI 20 s): the `ai` module's
 * draft and rewrite, or a configured stand-in for demos without the AI server.
 */
public interface SolutionFormAi {

    /**
     * The AI draft of the issue's Solution, or — when the AI is unavailable, too slow or gives an unusable answer — the
     * issue summary and description in Symptoms with the other sections empty (AC-002.1, AC-002.2). Never throws.
     */
    SolutionFormDraft draft(String summary, String description);

    /**
     * The five sections rewritten by AI (AC-007.1).
     *
     * @throws vn.tsolve.ai.exception.AiRewriteUnavailableException (503 MSG68) when the rewrite fails or is too slow
     */
    SolutionSections rewrite(SolutionSections sections);
}
