package vn.tsolve.wiki.dto;

import java.util.List;
import java.util.UUID;

public record WikiPermissionCandidates(List<Department> departments, List<User> users) {
    public record Department(UUID departmentId, String name) {}

    public record User(UUID userId, String fullName, String email, String homeDepartment) {}
}
