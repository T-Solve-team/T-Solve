package vn.tsolve.workspace.service;

import java.util.UUID;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;

public interface DepartmentService {
    /**
     * Creates the Department with its Space and audit entry in one transaction. In-process callers must pass a request
     * already validated against the {@code @Valid} rules of {@link CreateDepartmentRequest} and check the Admin role
     * themselves, as DepartmentController does.
     */
    DepartmentRefResponse create(CreateDepartmentRequest request);

    /** Reads an Active Department; a missing or Deleted one throws NotFoundException (MSG04). */
    DepartmentRefResponse get(UUID departmentId);

    /**
     * Renames an Active Department and its Space and records the audit entry in one transaction. A name equal to the
     * current one changes nothing. In-process callers must pass a request already validated against the {@code @Valid}
     * rules of {@link UpdateDepartmentRequest} and check the Admin role themselves, as DepartmentController does.
     */
    DepartmentRefResponse update(UUID departmentId, UpdateDepartmentRequest request);
}
