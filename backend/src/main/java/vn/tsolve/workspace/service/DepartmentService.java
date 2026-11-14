package vn.tsolve.workspace.service;

import java.util.List;
import java.util.UUID;
import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;
import vn.tsolve.workspace.dto.DepartmentDetailResponse;
import vn.tsolve.workspace.dto.DepartmentSummaryResponse;
import vn.tsolve.workspace.dto.UpdateDepartmentRequest;

public interface DepartmentService {
    /**
     * Creates the Department with its Space and audit entry in one transaction. In-process callers must pass a request
     * already validated against the {@code @Valid} rules of {@link CreateDepartmentRequest} and check the Admin role
     * themselves, as DepartmentController does.
     */
    DepartmentRefResponse create(CreateDepartmentRequest request);

    /** Reads an Active Department with its Project count; a missing or Deleted one throws NotFoundException (MSG04). */
    DepartmentDetailResponse get(UUID departmentId);

    /** The Active Departments, plus the Deleted ones when asked, sorted by name ignoring case. */
    List<DepartmentSummaryResponse> list(boolean includeDeleted);

    /**
     * Deletes an Active Department in one transaction: status Deleted, Active Projects Removed, default Project
     * cleared, Space Hidden, audit entry (GBR-17). Erases nothing. A missing or Deleted one throws NotFoundException
     * (MSG04). In-process callers check the Admin role themselves.
     */
    void delete(UUID departmentId);

    /**
     * Renames an Active Department and its Space and records the audit entry in one transaction. A name equal to the
     * current one changes nothing. In-process callers must pass a request already validated against the {@code @Valid}
     * rules of {@link UpdateDepartmentRequest} and check the Admin role themselves, as DepartmentController does.
     */
    DepartmentRefResponse update(UUID departmentId, UpdateDepartmentRequest request);
}
