package vn.tsolve.tracker.exception;

import vn.tsolve.common.exception.CodedException;

/**
 * Jira refused the call (401, 403, 404 or another 4xx): a retry would fail the same way (AC-012.8). It carries only
 * the HTTP status and never a cause or Jira's body.
 */
public class JiraRejectedException extends CodedException {

    private final int status;

    public JiraRejectedException(int status) {
        super("Jira rejected the call: " + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
