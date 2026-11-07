package vn.tsolve.tracker.exception;

import vn.tsolve.common.exception.CodedException;

/**
 * Jira did not answer, or answered 5xx or 429: a retry may succeed (AC-012.7). It carries only the HTTP status (0 when
 * there was no answer) and never a cause or Jira's body, which may quote the issue or the credentials.
 */
public class JiraUnavailableException extends CodedException {

    private final int status;

    public JiraUnavailableException(int status) {
        super("Jira unavailable: " + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
