package vn.tsolve.analytics.service.impl;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.analytics.exception.InvalidDashboardFilterException;
import vn.tsolve.analytics.repository.DashboardRepository;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;

/** Null departments means the whole Workspace; projectId is always a public id. */
public record DashboardScope(Set<Long> departments, UUID projectId, Long ownDepartment) {
    public static DashboardScope resolve(
            TSolveUser user, String departmentId, String projectId, DashboardRepository repository) {
        UUID departmentPublicId = parseId(departmentId);
        UUID projectPublicId = parseId(projectId);
        if (user.roles().contains(Role.ADMIN)) {
            var department = departmentPublicId == null
                    ? null
                    : repository
                            .findDepartment(departmentPublicId)
                            .orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
            var project = projectPublicId == null
                    ? null
                    : repository
                            .findProject(projectPublicId)
                            .orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
            if (department != null && project != null && project.departmentId() != department.id()) {
                throw new InvalidDashboardFilterException("MSG06");
            }
            return new DashboardScope(department == null ? null : Set.of(department.id()), projectPublicId, null);
        }
        // A Project Manager gets 403 for an unknown id too, so the answer never tells whether another
        // Department's id exists (AC-004.5, AC-004.7).
        long ownDepartment =
                repository.findUserDepartment(user.id()).orElseThrow(() -> new AccessDeniedException("MSG08"));
        if (departmentPublicId != null
                && repository
                        .findDepartment(departmentPublicId)
                        .filter(department -> department.id() == ownDepartment)
                        .isEmpty()) {
            throw new AccessDeniedException("MSG08");
        }
        if (projectPublicId != null
                && repository
                        .findProject(projectPublicId)
                        .filter(project -> project.departmentId() == ownDepartment)
                        .isEmpty()) {
            throw new AccessDeniedException("MSG08");
        }
        return new DashboardScope(Set.of(ownDepartment), projectPublicId, ownDepartment);
    }

    private static UUID parseId(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            UUID id = UUID.fromString(raw);
            if (!id.toString().equalsIgnoreCase(raw)) {
                throw new InvalidDashboardFilterException("MSG06");
            }
            return id;
        } catch (IllegalArgumentException invalid) {
            throw new InvalidDashboardFilterException("MSG06");
        }
    }
}
