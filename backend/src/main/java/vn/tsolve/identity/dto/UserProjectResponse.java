package vn.tsolve.identity.dto;

import java.util.UUID;

public record UserProjectResponse(
        UUID projectId, String name, DepartmentRefResponse department, boolean isActive, UUID managerId) {}
