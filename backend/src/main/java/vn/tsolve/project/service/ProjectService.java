package vn.tsolve.project.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;

public interface ProjectService {

    /** Tests the Jira connection and prefills settings without persisting changes. */
    ProjectConnectionTestResponse testConnection(ProjectConnectionTestRequest request);

    /** The public id of the ACTIVE Project linked to this Jira project key; empty when none is. */
    Optional<UUID> findActivePublicIdByJiraProjectKey(String jiraProjectKey);
}
