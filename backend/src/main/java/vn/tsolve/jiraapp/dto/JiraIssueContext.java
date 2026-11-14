package vn.tsolve.jiraapp.dto;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.jiraapp.exception.JiraAskerNotLinkedException;
import vn.tsolve.jiraapp.exception.JiraIssueAccessDeniedException;

/**
 * What the Jira App may do on one issue, read inside the caller's Workspace: the linked user, the ACTIVE Project whose
 * Jira project key is the issue's, and whether the user is a member of it (jira-app AC-001.2, AC-001.3, AC-008.1).
 */
public record JiraIssueContext(Optional<TSolveUser> user, Optional<UUID> projectId, boolean member) {

    /** The Jira account is linked to an active T-Solve Account (otherwise MSG62). */
    public boolean linked() {
        return user.isPresent();
    }

    /** The linked member and the Project, or 403: MSG62 when not linked, MSG08 when not a member. */
    public Member requireMember() {
        TSolveUser linkedUser = user.orElseThrow(JiraAskerNotLinkedException::new);
        if (!member || projectId.isEmpty()) {
            throw new JiraIssueAccessDeniedException();
        }
        return new Member(linkedUser, projectId.get());
    }

    public record Member(TSolveUser user, UUID projectId) {}
}
