package vn.tsolve.workspace.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.workspace.entity.WorkspaceInfo;

public interface WorkspaceInfoRepository extends JpaRepository<WorkspaceInfo, UUID> {}
