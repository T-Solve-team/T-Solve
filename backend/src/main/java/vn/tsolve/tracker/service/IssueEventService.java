package vn.tsolve.tracker.service;

import vn.tsolve.tracker.dto.IssueEventPayload;
import vn.tsolve.tracker.entity.IssueEventType;

public interface IssueEventService {

    /**
     * Records the event as RECEIVED under the current Workspace with its encrypted system token; an event already
     * recorded is skipped (AC-003.1, AC-003.4, AC-003.7). Call with the site's Workspace set.
     */
    void receive(String cloudId, IssueEventType eventType, IssueEventPayload payload, String systemToken);
}
