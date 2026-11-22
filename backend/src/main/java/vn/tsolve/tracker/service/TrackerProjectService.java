package vn.tsolve.tracker.service;

import java.util.List;
import java.util.Optional;
import vn.tsolve.tracker.dto.ChatbotProjectCheck;
import vn.tsolve.tracker.dto.TrackerProjectLookup;
import vn.tsolve.tracker.dto.TrackerRole;
import vn.tsolve.tracker.dto.TrackerRoleActors;

/** Read-only Jira lookups (KAN-127, KAN-121); client failures are returned as outcomes, never thrown. */
public interface TrackerProjectService {

    TrackerProjectLookup findProject(String projectKey);

    /** Whether the Chatbot can create Task issues in the project as the service account (workspace AC-012.7). */
    ChatbotProjectCheck checkChatbotProject(String projectKey);

    /**
     * The project's roles, read as the Workspace's Jira App; empty when there is no token or Jira refuses, fails or does
     * not answer (project-integration AC-011.1, AC-011.4).
     */
    Optional<List<TrackerRole>> projectRoles(String projectKey);

    /** The actors of one project role; empty in the same cases, and for a role the project does not have (AC-011.2). */
    Optional<TrackerRoleActors> roleActors(String projectKey, long roleId);
}
