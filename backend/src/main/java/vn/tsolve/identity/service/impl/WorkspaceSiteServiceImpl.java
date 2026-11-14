package vn.tsolve.identity.service.impl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vn.tsolve.identity.service.WorkspaceSiteService;
import vn.tsolve.identity.signin.port.WorkspaceLookup;
import vn.tsolve.identity.signin.port.WorkspaceRef;

@Service
public class WorkspaceSiteServiceImpl implements WorkspaceSiteService {
    private final ObjectProvider<WorkspaceLookup> lookups;

    public WorkspaceSiteServiceImpl(ObjectProvider<WorkspaceLookup> lookups) {
        this.lookups = lookups;
    }

    @Override
    public Optional<String> siteHost(UUID workspaceId) {
        WorkspaceLookup lookup = lookups.getIfAvailable();
        return lookup == null
                ? Optional.empty()
                : lookup.findById(workspaceId).map(WorkspaceRef::siteHost).filter(host -> !host.isBlank());
    }
}
