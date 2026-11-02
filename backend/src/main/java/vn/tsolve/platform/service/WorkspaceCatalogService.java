package vn.tsolve.platform.service;

import java.util.Optional;
import vn.tsolve.platform.dto.WorkspaceSummary;

public interface WorkspaceCatalogService {
    Optional<WorkspaceSummary> findById(long id);

    Optional<WorkspaceSummary> findBySiteName(String siteName);
}
