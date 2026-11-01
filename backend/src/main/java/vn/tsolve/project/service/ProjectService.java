package vn.tsolve.project.service;

import java.util.Optional;

public interface ProjectService {

    /** The id of the ACTIVE Project linked to this Jira project key; empty when none is. */
    Optional<Long> findActiveIdByJiraProjectKey(String jiraProjectKey);
}
