package vn.tsolve.ticket.service;

import java.util.Optional;
import java.util.UUID;

/**
 * What the import needs to know about Projects. The workspace module owns Projects; until it exists a stub answers
 * (design: Stub safety).
 */
public interface ProjectAccess {

    /** True when the user is the Project Manager of this Project and the Project is Active. */
    boolean managesActive(String user, UUID projectId);

    /** False when the Project was removed or its Department deleted (MSG40). */
    boolean isActive(UUID projectId);

    /** The key of the Jira project linked to this Project; empty when it has none (Jira import, MSG50). */
    Optional<String> jiraProjectKey(UUID projectId);
}
