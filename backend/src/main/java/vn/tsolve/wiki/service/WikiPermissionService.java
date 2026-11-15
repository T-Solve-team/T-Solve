package vn.tsolve.wiki.service;

import java.util.UUID;
import vn.tsolve.wiki.dto.GrantWikiPermissionsRequest;
import vn.tsolve.wiki.dto.WikiPermissionCandidates;
import vn.tsolve.wiki.dto.WikiSpaceSettings;

public interface WikiPermissionService {
    WikiSpaceSettings getSettings(UUID userId, UUID spaceId);

    WikiPermissionCandidates listCandidates(UUID userId, UUID spaceId);

    void grant(UUID userId, UUID spaceId, GrantWikiPermissionsRequest request);

    void revoke(UUID userId, UUID permissionId);
}
