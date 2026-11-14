package vn.tsolve.workspace.dto;

import java.util.UUID;

/** One row of the 2.2 Departments table; status is ACTIVE or DELETED (AC-005.16). */
public record DepartmentSummaryResponse(UUID departmentId, String name, String status) {}
