package vn.tsolve.wiki.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WikiSpaceSettings(
        UUID spaceId,
        String name,
        String departmentName,
        String knowledgeManager,
        OwnDepartment ownDepartment,
        List<DepartmentPermission> departments,
        List<UserPermission> users) {
    public record OwnDepartment(UUID departmentId, String name, boolean locked) {}

    public record DepartmentPermission(
            UUID permissionId, UUID departmentId, String name, String grantedBy, Instant grantedAt) {}

    public record UserPermission(
            UUID permissionId,
            UUID userId,
            String fullName,
            String email,
            String homeDepartment,
            boolean active,
            String grantedBy,
            Instant grantedAt) {}
}
