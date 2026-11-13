package vn.tsolve.common.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record DepartmentRef(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID departmentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
