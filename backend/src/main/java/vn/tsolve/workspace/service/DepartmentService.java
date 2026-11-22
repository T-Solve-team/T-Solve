package vn.tsolve.workspace.service;

import java.util.UUID;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.DepartmentDetailResponse;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;

public interface DepartmentService {
    /**
     * Creates the Department with its Space and audit entry in one transaction. In-process callers must pass a request
     * already validated against the {@code @Valid} rules of {@link CreateDepartmentRequest} and check the Admin role
     * themselves, as DepartmentController does.
     */
    DepartmentRefResponse create(CreateDepartmentRequest request);

    /**
     * Reads an Active Department with its default Project and Active Projects; a missing or Deleted one throws
     * NotFoundException (MSG04).
     */
    DepartmentDetailResponse get(UUID departmentId);

    /**
     * Renames an Active Department and its Space, sets its default Project and records the audit entry in one
     * transaction; a new default Project is checked in Jira first, outside the transaction. A save that changes
     * neither changes nothing. In-process callers must pass a request already validated against the {@code @Valid}
     * rules of {@link UpdateDepartmentRequest} and check the Admin role themselves, as DepartmentController does.
     */
    DepartmentDetailResponse update(UUID departmentId, UpdateDepartmentRequest request);
}
