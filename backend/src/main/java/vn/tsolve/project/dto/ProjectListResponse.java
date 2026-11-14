package vn.tsolve.project.dto;

import java.util.List;
import vn.tsolve.identity.dto.DepartmentRefResponse;

public record ProjectListResponse(DepartmentRefResponse department, List<ProjectRowResponse> projects) {}
