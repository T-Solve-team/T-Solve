package vn.tsolve.workspace.dto;

import java.util.UUID;

/** An Active Department with the number of its Projects in any status, the {count} of MSG45 (AC-005.1). */
public record DepartmentDetailResponse(UUID departmentId, String name, int projectCount) {}
