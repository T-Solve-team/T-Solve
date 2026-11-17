package vn.tsolve.tracker.exception;

import java.util.List;

/**
 * The service account authenticated but lacks required Jira permissions (workspace AC-011.1). It carries only the
 * permission keys T-Solve asked for, never Jira's body.
 */
public class JiraPermissionMissingException extends JiraRejectedException {

    private final List<String> missingPermissions;

    public JiraPermissionMissingException(List<String> missingPermissions) {
        super(403);
        this.missingPermissions = List.copyOf(missingPermissions);
    }

    public List<String> getMissingPermissions() {
        return missingPermissions;
    }
}
