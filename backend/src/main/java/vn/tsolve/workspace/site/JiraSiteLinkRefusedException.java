package vn.tsolve.workspace.site;

/** Carries only a safe reason, never the site's response, credentials or an underlying exception. */
public class JiraSiteLinkRefusedException extends RuntimeException {

    public enum Reason {
        LINKED_ELSEWHERE,
        UNREADABLE
    }

    private final Reason reason;

    public JiraSiteLinkRefusedException(Reason reason) {
        super("Jira site link refused: " + reason);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
