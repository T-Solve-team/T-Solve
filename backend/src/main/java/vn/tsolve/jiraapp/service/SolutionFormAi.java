package vn.tsolve.jiraapp.service;

import vn.tsolve.ticket.dto.SolutionSections;

/**
 * The AI of the Solution Form, within the Jira App's time budget (Forge allows 25 s, the UI 20 s): the `ai` module's
 * rewrite (no AI draft since KAN-371), or a configured stand-in for demos without the AI server.
 */
public interface SolutionFormAi {

    /**
     * The five sections rewritten by AI (AC-007.1).
     *
     * @throws vn.tsolve.ai.exception.AiRewriteUnavailableException (503 MSG68) when the rewrite fails or is too slow
     */
    SolutionSections rewrite(SolutionSections sections);
}
