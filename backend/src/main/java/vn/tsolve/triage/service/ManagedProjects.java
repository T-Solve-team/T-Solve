package vn.tsolve.triage.service;

import java.util.List;
import vn.tsolve.triage.dto.ManagedProject;

/**
 * The Projects a user manages ({@code project_membership.role_in_project = PROJECT_MANAGER}), only those an {@code
 * ACTIVE} account holding the PROJECT_MANAGER Role manages in an {@code ACTIVE} Project and Department.
 */
public interface ManagedProjects {

    List<ManagedProject> of(String user);
}
