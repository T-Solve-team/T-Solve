package vn.tsolve.common.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ProjectMembership(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID projectId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String jiraProjectKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID departmentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String departmentName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true)
        ProjectRole roleInProject) {}
