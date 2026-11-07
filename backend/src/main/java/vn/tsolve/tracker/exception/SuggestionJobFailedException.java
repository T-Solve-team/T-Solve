package vn.tsolve.tracker.exception;

import vn.tsolve.common.exception.CodedException;

/**
 * A suggestion job that failed and should be retried by the worker (AC-011.6). It carries only the issue key and
 * the error type, and never a cause: JobRunr stores the exception with its causes in the shared Jobs database, and
 * a cause's message may quote the issue text.
 */
public class SuggestionJobFailedException extends CodedException {

    public SuggestionJobFailedException(String issueKey, String errorType) {
        super("Suggestion job failed for " + issueKey + ": " + errorType);
    }
}
