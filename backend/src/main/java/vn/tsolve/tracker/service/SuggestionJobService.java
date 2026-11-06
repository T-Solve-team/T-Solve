package vn.tsolve.tracker.service;

import vn.tsolve.tracker.dto.SuggestionJobRequest;

/** The suggestion job of one new issue (STORY-011); the job runner calls it, never a controller. */
public interface SuggestionJobService {

    /**
     * Asks search for a suggestion and stores it in the issue's suggestion result (AC-011.3, AC-011.4). Never
     * throws: no suggestion, a failed search, or a result that is gone or already filled changes nothing
     * (AC-011.5 to AC-011.7).
     */
    void run(SuggestionJobRequest request);
}
