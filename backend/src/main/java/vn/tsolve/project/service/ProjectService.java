package vn.tsolve.project.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.project.dto.CreateProjectRequest;
import vn.tsolve.project.dto.ProjectConnectionTestRequest;
import vn.tsolve.project.dto.ProjectConnectionTestResponse;
import vn.tsolve.project.dto.ProjectSaveResult;

public interface ProjectService {

    /** Tests the Jira connection and prefills settings without persisting changes. */
    ProjectConnectionTestResponse testConnection(ProjectConnectionTestRequest request);

    /**
     * Saves a new Project, or reconnects the eligible Removed one, with its manager membership and audit entry in one
     * transaction, after a fresh Jira read.
     */
    ProjectSaveResult create(CreateProjectRequest request);

    /** The public id of the ACTIVE Project linked to this Jira project key; empty when none is. */
    Optional<UUID> findActivePublicIdByJiraProjectKey(String jiraProjectKey);
}
