package vn.tsolve.triage.service;

import java.util.List;
import vn.tsolve.triage.dto.ManagedProject;

/**
 * The Projects a user manages ({@code project_membership.role_in_project = PROJECT_MANAGER}). The identity module
 * owns users and memberships; until it provides this, a fail-closed stand-in answers (design: Backend history API).
 */
public interface ManagedProjects {

    List<ManagedProject> of(String user);
}
