package vn.tsolve.triage.service.impl;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.tsolve.triage.dto.ManagedProject;
import vn.tsolve.triage.service.ManagedProjects;

/**
 * Stand-in until the identity module provides {@code project_membership}; delete it when the real {@link
 * ManagedProjects} lands. Fails closed: nobody manages a Project, so the history shows no row.
 */
@Component
public class NoManagedProjects implements ManagedProjects {

    @Override
    public List<ManagedProject> of(String user) {
        return List.of();
    }
}
