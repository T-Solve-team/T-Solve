package vn.tsolve.identity.service;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceSiteService {
    Optional<String> siteHost(UUID workspaceId);
}
