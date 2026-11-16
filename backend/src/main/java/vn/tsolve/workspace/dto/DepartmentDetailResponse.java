package vn.tsolve.workspace.dto;

import java.util.List;
import java.util.UUID;

/** 2.3 Department Detail: {@code defaultProject} is null when there is none or it is not Active (AC-012.3). */
public record DepartmentDetailResponse(
        UUID departmentId,
        String name,
        DepartmentProjectOptionResponse defaultProject,
        List<DepartmentProjectOptionResponse> projects) {}
