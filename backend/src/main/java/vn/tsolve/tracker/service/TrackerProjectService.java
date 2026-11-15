package vn.tsolve.tracker.service;

import vn.tsolve.tracker.dto.TrackerProjectLookup;

/** Read-only Jira lookup for the Project module (KAN-127); client failures are returned as outcomes, never thrown. */
public interface TrackerProjectService {

    TrackerProjectLookup findProject(String projectKey);
}
