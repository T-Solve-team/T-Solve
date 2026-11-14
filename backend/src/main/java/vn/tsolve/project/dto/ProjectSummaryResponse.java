package vn.tsolve.project.dto;

import java.util.UUID;
import vn.tsolve.project.entity.ProjectStatus;

public record ProjectSummaryResponse(
        UUID id, String jiraProjectKey, String departmentName, String projectManagerName, ProjectStatus status) {}
