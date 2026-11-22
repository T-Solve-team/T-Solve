package vn.tsolve.jiraapp.service;

import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.jiraapp.dto.IssueUpdatedPayload;

/** The Forge {@code avi:jira:updated:issue} trigger (jira-app AC-005.6 to AC-005.10, KAN-371). */
public interface IssueUpdatedService {

    /**
     * Records whether a Project's issue is in Done and, on a move into Done, creates the Draft of the assignee (else
     * the mover). The Workspace comes only from the verified FIT's site. An event that cannot be read, a site with no
     * Workspace or an issue of no Project changes nothing.
     */
    void receive(JiraAppPrincipal caller, IssueUpdatedPayload event);
}
