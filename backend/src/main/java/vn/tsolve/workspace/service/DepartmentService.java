package vn.tsolve.workspace.service;

import vn.tsolve.identity.dto.DepartmentRefResponse;
import vn.tsolve.workspace.dto.CreateDepartmentRequest;

public interface DepartmentService {
    /**
     * Creates the Department with its Space and audit entry in one transaction. In-process callers must pass a request
     * already validated against the {@code @Valid} rules of {@link CreateDepartmentRequest} and check the Admin role
     * themselves, as DepartmentController does.
     */
    DepartmentRefResponse create(CreateDepartmentRequest request);
}
