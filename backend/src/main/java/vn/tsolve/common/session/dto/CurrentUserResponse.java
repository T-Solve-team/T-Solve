package vn.tsolve.common.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.tsolve.common.security.Role;

@Schema(name = "CurrentUser")
public record CurrentUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserSummaryResponse user,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Role> roles,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        DepartmentRef department,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DepartmentRef> departments,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProjectMembership> projects,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SpaceRef> managedSpaces,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean workspaceReady) {}
