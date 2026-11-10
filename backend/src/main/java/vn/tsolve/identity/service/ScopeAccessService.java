package vn.tsolve.identity.service;

import java.util.UUID;

/** Checks public UUIDs against the database of the Workspace bound by TenantScope. */
public interface ScopeAccessService {
    boolean canAccessDepartment(UUID accountId, UUID departmentId);

    boolean canAccessProject(UUID accountId, UUID projectId);

    boolean canAccessSpace(UUID accountId, UUID spaceId);

    void requireDepartment(UUID accountId, UUID departmentId);

    void requireProject(UUID accountId, UUID projectId);

    void requireSpace(UUID accountId, UUID spaceId);
}
