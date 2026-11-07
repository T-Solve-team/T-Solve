package vn.tsolve.tracker.service;

import vn.tsolve.tracker.dto.SuggestionJobRequest;

/**
 * Hands a suggestion job to the job runner and returns at once (AC-011.1). The runner is the {@code worker} module's
 * (JobRunr, KAN-161); until it exists an in-process stand-in runs the job.
 */
public interface SuggestionJobQueue {

    /** Never throws because the job could not be queued: a lost job leaves the issue without a suggestion. */
    void enqueue(SuggestionJobRequest request);
}
