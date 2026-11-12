package vn.tsolve.project.dto;

import java.util.List;
import java.util.UUID;

public record ProjectResponse(
        UUID projectId,
        UUID departmentId,
        String jiraProjectKey,
        List<String> doneStatusIds,
        int reviewRange,
        String status) {}
