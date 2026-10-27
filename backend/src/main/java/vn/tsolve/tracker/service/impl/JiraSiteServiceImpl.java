package vn.tsolve.tracker.service.impl;

import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.tsolve.common.security.JiraSiteLookup;
import vn.tsolve.common.security.WorkspaceContext;
import vn.tsolve.tracker.entity.JiraSite;
import vn.tsolve.tracker.repository.JiraSiteRepository;

/** Jira site registration (Task 4 adds {@code JiraSiteService}); for now the cloudId lookup of the FIT chain. */
@Service
public class JiraSiteServiceImpl implements JiraSiteLookup {

    private final JiraSiteRepository jiraSiteRepository;

    public JiraSiteServiceImpl(JiraSiteRepository jiraSiteRepository) {
        this.jiraSiteRepository = jiraSiteRepository;
    }

    // The Workspace is not known yet: that is what this finds, so it reads across all of them.
    @Override
    public Optional<Long> findWorkspaceId(String cloudId) {
        return WorkspaceContext.callAs(
                WorkspaceContext.ROOT,
                () -> jiraSiteRepository.findByCloudId(cloudId).map(JiraSite::getWorkspaceId));
    }
}
