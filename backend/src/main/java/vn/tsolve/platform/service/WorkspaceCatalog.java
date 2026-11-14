package vn.tsolve.platform.service;

import java.util.Optional;
import java.util.UUID;
import vn.tsolve.platform.dto.WorkspaceSummary;

public interface WorkspaceCatalog { // STORY-004
    Optional<WorkspaceSummary> findById(UUID workspaceId);

    Optional<WorkspaceSummary> findBySiteName(String siteName);

    Optional<WorkspaceSummary> findByCloudId(String cloudId);

    WorkspaceSummary linkJiraSite(String siteUrl); // bound Workspace; AC-004.19 – AC-004.24

    void claimEmail(String email); // bound Workspace; AC-004.25, AC-004.26
}
