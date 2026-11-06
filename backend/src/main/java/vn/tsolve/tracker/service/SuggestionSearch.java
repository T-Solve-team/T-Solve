package vn.tsolve.tracker.service;

import java.util.Optional;
import vn.tsolve.tracker.dto.IssueSuggestion;

/**
 * The AI Solution Suggestion for a new issue, owned by {@code search} (KAN-47: retrieval, relevance threshold, LLM
 * text). Declared here because the tracker calls it (spec: Required from other modules); a configured stand-in
 * answers until KAN-47 implements it.
 */
public interface SuggestionSearch {

    /**
     * A suggestion for the issue, or empty when no source passes the relevance threshold. Throws when search fails
     * or does not answer in time.
     */
    Optional<IssueSuggestion> suggest(long projectId, String summary, String description);
}
