package vn.tsolve.identity.service.impl;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.session.dto.ProjectMembership;
import vn.tsolve.common.session.lookup.ProjectMembershipLookup;
import vn.tsolve.identity.repository.ScopeAccessRepository;

/** The Projects of {@code GET /api/v1/me} (be-foundation AC-003.2); without it /me had none (BUG-299). */
@Service
public class ProjectMembershipLookupImpl implements ProjectMembershipLookup {
    private final ScopeAccessRepository repository;

    public ProjectMembershipLookupImpl(ScopeAccessRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMembership> findProjects(UUID userId) {
        return repository.projects(userId);
    }
}
