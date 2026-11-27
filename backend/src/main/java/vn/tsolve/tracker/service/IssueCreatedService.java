package vn.tsolve.tracker.service;

import vn.tsolve.common.security.JiraAppPrincipal;
import vn.tsolve.tracker.dto.IssueCreatedPayload;

public interface IssueCreatedService {

    /**
     * Stores a suggestion result for a new issue of an ACTIVE T-Solve Project (AC-003.1). An issue of no such
     * Project (AC-003.8) or one already stored (AC-003.4) changes nothing. A newly stored issue gets one
     * suggestion job, queued after its row is committed (AC-011.1, AC-011.2). All of it runs bound to the Workspace
     * of the caller's Jira site; a caller with no Workspace changes nothing (AC-003.9).
     */
    void receive(JiraAppPrincipal caller, IssueCreatedPayload payload);
}
