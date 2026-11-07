package vn.tsolve.tracker.service;

import java.util.UUID;

/** The suggestion job of one new issue (STORY-011); the job runner calls it, never a controller. */
public interface SuggestionJobService {

    /**
     * Asks search for a suggestion with the issue text stored in the referenced suggestion result, and stores it
     * there (AC-011.3, AC-011.4). No suggestion, or a result that is gone or already filled, changes nothing
     * (AC-011.5, AC-011.7). Any failure throws {@code SuggestionJobFailedException} so the worker retries the job
     * (AC-011.6).
     */
    void run(UUID suggestionId);
}
