package vn.tsolve.tracker.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.tracker.dto.IssueSuggestion;

/**
 * The AI Solution Suggestion for a new issue: retrieval, relevance threshold and LLM text. Declared here because the
 * tracker calls it (spec: Required from other modules); triage's {@code SuggestionGenerator} implements it.
 */
public interface SuggestionSearch {

    /**
     * A suggestion for the issue, or empty when no source passes the relevance threshold. Throws when search fails
     * or does not answer in time.
     */
    Optional<IssueSuggestion> suggest(UUID projectId, String summary, String description);
}
